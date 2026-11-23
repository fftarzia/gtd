'use strict';

jest.mock('../../../models', () => ({
    NotificationEmailDelivery: {
        findOrCreate: jest.fn(),
        update: jest.fn(),
        findByPk: jest.fn(),
        findAll: jest.fn(),
        destroy: jest.fn(),
    },
    Notification: { findOne: jest.fn() },
}));
jest.mock('../../../services/emailService', () => ({
    isEmailEnabled: jest.fn(),
    sendEmail: jest.fn(),
}));
jest.mock('../../../services/logService', () => ({
    logError: jest.fn(),
}));

const { NotificationEmailDelivery, Notification } = require('../../../models');
const { isEmailEnabled, sendEmail } = require('../../../services/emailService');
const queue = require('../../../services/notificationEmailQueue');

const DATE = new Date('2026-11-23T12:00:00.000Z');

function makeDelivery(overrides = {}) {
    return {
        id: 12,
        kind: 'inbox',
        source_uid: 'inbox-abc',
        user_id: 42,
        recipient: 'user@example.test',
        subject: 'Nuova voce Inbox',
        body_text: 'Prima riga\nSeconda riga',
        body_html: null,
        attempts: 1,
        status: 'sending',
        update: jest.fn(async function (values) {
            Object.assign(this, values);
            return this;
        }),
        ...overrides,
    };
}

describe('PostgreSQL email notification outbox', () => {
    let delivery;

    beforeEach(() => {
        jest.clearAllMocks();
        delivery = makeDelivery();
        isEmailEnabled.mockReturnValue(true);
        sendEmail.mockResolvedValue({ success: true });
        NotificationEmailDelivery.findOrCreate.mockResolvedValue([
            delivery,
            true,
        ]);
        NotificationEmailDelivery.findByPk.mockResolvedValue(delivery);
        NotificationEmailDelivery.update.mockResolvedValue([1]);
        NotificationEmailDelivery.findAll.mockResolvedValue([]);
        NotificationEmailDelivery.destroy.mockResolvedValue(0);
    });

    it('does not persist new messages when email is disabled', async () => {
        isEmailEnabled.mockReturnValue(false);
        const result = await queue.enqueue({
            userId: 42,
            kind: 'inbox',
            sourceUid: 'inbox-abc',
            recipient: 'user@example.test',
            subject: 'Subject',
            text: 'Note',
        });
        expect(result).toBeNull();
        expect(NotificationEmailDelivery.findOrCreate).not.toHaveBeenCalled();
    });

    it('deduplicates by kind and source UID', async () => {
        const params = {
            userId: 42,
            kind: 'inbox',
            sourceUid: 'inbox-abc',
            recipient: 'user@example.test',
            subject: 'Subject',
            text: 'First line\nSecond line',
        };
        await queue.enqueue(params);
        expect(NotificationEmailDelivery.findOrCreate).toHaveBeenCalledWith({
            where: { dedup_key: 'inbox:inbox-abc' },
            defaults: expect.objectContaining({
                user_id: 42,
                body_text: params.text,
                status: 'pending',
            }),
        });
    });

    it('accepts an explicit deduplication key for recurring events', async () => {
        await queue.enqueue({
            userId: 42,
            kind: 'notification',
            sourceUid: 'ephemeral-uid',
            dedupKey: 'notification:42:task_overdue:task1:2026-11-23',
            recipient: 'user@example.test',
            subject: 'Overdue',
            text: 'Task overdue',
        });
        expect(NotificationEmailDelivery.findOrCreate).toHaveBeenCalledWith(
            expect.objectContaining({
                where: {
                    dedup_key: 'notification:42:task_overdue:task1:2026-11-23',
                },
            })
        );
    });

    it('waits for transaction commit before dispatching', async () => {
        const transaction = { afterCommit: jest.fn() };
        await queue.enqueue({
            userId: 42,
            kind: 'inbox',
            sourceUid: 'inbox-abc',
            recipient: 'user@example.test',
            subject: 'Subject',
            text: 'Note',
            transaction,
        });
        expect(transaction.afterCommit).toHaveBeenCalledTimes(1);
        expect(transaction.afterCommit).toHaveBeenCalledWith(
            expect.any(Function)
        );
        expect(NotificationEmailDelivery.findOrCreate).toHaveBeenCalledWith(
            expect.objectContaining({ transaction })
        );
    });

    it('does not register another send for an existing outbox row', async () => {
        NotificationEmailDelivery.findOrCreate.mockResolvedValue([
            delivery,
            false,
        ]);
        const transaction = { afterCommit: jest.fn() };
        await queue.enqueue({
            userId: 42,
            kind: 'inbox',
            sourceUid: 'inbox-abc',
            recipient: 'user@example.test',
            subject: 'Subject',
            text: 'Note',
            transaction,
        });
        expect(transaction.afterCommit).not.toHaveBeenCalled();
    });

    it('sends the stored plain text exactly and records success', async () => {
        const result = await queue.deliver(12, { now: DATE });
        expect(sendEmail).toHaveBeenCalledWith({
            to: 'user@example.test',
            subject: 'Nuova voce Inbox',
            text: 'Prima riga\nSeconda riga',
            html: null,
        });
        expect(result.status).toBe('sent');
        expect(result.next_attempt_at).toBeNull();
        expect(result.sent_at).toBeInstanceOf(Date);
        expect(Notification.findOne).not.toHaveBeenCalled();
    });

    it('does not send when another worker already claimed the row', async () => {
        NotificationEmailDelivery.update.mockResolvedValueOnce([0]);
        const result = await queue.deliver(12, { now: DATE });
        expect(result).toBeNull();
        expect(sendEmail).not.toHaveBeenCalled();
    });

    it('retries a transient failure after one minute', async () => {
        sendEmail.mockResolvedValue({
            success: false,
            reason: 'SMTP connection failed',
        });
        const result = await queue.deliver(12, { now: DATE });
        expect(result.status).toBe('pending');
        expect(result.attempts).toBe(1);
        expect(result.next_attempt_at).toEqual(
            new Date(DATE.getTime() + 60 * 1000)
        );
        expect(result.last_error).toBe('SMTP connection failed');
    });

    it('uses progressive delays for later attempts', async () => {
        delivery.attempts = 5;
        sendEmail.mockResolvedValue({ success: false, reason: 'Rate limited' });
        await queue.deliver(12, { now: DATE });
        expect(delivery.next_attempt_at).toEqual(
            new Date(DATE.getTime() + 60 * 60 * 1000)
        );
    });

    it('stops after eight attempts', async () => {
        delivery.attempts = 8;
        sendEmail.mockResolvedValue({
            success: false,
            reason: 'SMTP unavailable',
        });
        await queue.deliver(12, { now: DATE });
        expect(delivery.status).toBe('failed');
        expect(delivery.next_attempt_at).toBeNull();
        expect(delivery.update).toHaveBeenCalledWith(
            expect.objectContaining({ status: 'failed' })
        );
    });

    it('retries a thrown SMTP error', async () => {
        sendEmail.mockRejectedValue(new Error('Socket closed'));
        await queue.deliver(12, { now: DATE });
        expect(delivery.status).toBe('pending');
        expect(delivery.last_error).toBe('Socket closed');
    });

    it('marks an application notification after SMTP has accepted it', async () => {
        delivery.kind = 'notification';
        delivery.source_uid = 'event-uid';
        const notification = { markChannelAsSent: jest.fn() };
        Notification.findOne.mockResolvedValue(notification);
        await queue.deliver(12, { now: DATE });
        expect(Notification.findOne).toHaveBeenCalledWith({
            where: { uid: 'event-uid', user_id: 42 },
        });
        expect(notification.markChannelAsSent).toHaveBeenCalledWith('email');
    });

    it('does not resend a successful email if tracking its source fails', async () => {
        delivery.kind = 'notification';
        Notification.findOne.mockRejectedValue(new Error('Row deleted'));
        await queue.deliver(12, { now: DATE });
        expect(delivery.status).toBe('sent');
        expect(sendEmail).toHaveBeenCalledTimes(1);
    });

    it('recovers stale sending rows and processes due deliveries', async () => {
        NotificationEmailDelivery.findAll.mockResolvedValue([{ id: 12 }]);
        const result = await queue.processDue({ now: DATE, limit: 3 });
        expect(NotificationEmailDelivery.update).toHaveBeenCalledTimes(3);
        expect(NotificationEmailDelivery.findAll).toHaveBeenCalledWith(
            expect.objectContaining({
                attributes: ['id'],
                limit: 3,
            })
        );
        expect(result).toEqual({ processed: 1, sent: 1 });
        expect(NotificationEmailDelivery.destroy).toHaveBeenCalledTimes(2);
    });

    it('provides retry policy for monitoring', () => {
        expect(queue.MAX_ATTEMPTS).toBe(8);
        expect(queue.RETRY_DELAYS_MIN).toEqual([1, 5, 15, 30, 60, 120, 240]);
    });
});
