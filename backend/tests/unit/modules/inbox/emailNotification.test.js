'use strict';

jest.mock('../../../../services/emailService', () => ({
    isEmailEnabled: jest.fn(),
}));
jest.mock('../../../../services/notificationEmailQueue', () => ({
    enqueue: jest.fn(),
}));
jest.mock('../../../../models', () => ({
    User: { findByPk: jest.fn() },
}));

const { User } = require('../../../../models');
const { isEmailEnabled } = require('../../../../services/emailService');
const { enqueue } = require('../../../../services/notificationEmailQueue');
const {
    notifyInboxCreated,
} = require('../../../../modules/inbox/emailNotification');

describe('Inbox email outbox', () => {
    const item = {
        uid: 'inbox-abc',
        content: 'Nota rapida per il cliente\nSeconda riga: prova #idee',
    };

    beforeEach(() => {
        jest.clearAllMocks();
        isEmailEnabled.mockReturnValue(true);
        enqueue.mockResolvedValue({ id: 12 });
    });

    it('does not queue mail when SMTP is disabled', async () => {
        isEmailEnabled.mockReturnValue(false);
        await notifyInboxCreated(1, item);
        expect(User.findByPk).not.toHaveBeenCalled();
        expect(enqueue).not.toHaveBeenCalled();
    });

    it('does not queue mail without opt-in', async () => {
        User.findByPk.mockResolvedValue({
            email: 'user@example.test',
            notification_preferences: { inboxCreated: { email: false } },
        });
        await notifyInboxCreated(1, item);
        expect(enqueue).not.toHaveBeenCalled();
    });

    it('does not queue mail without a recipient', async () => {
        User.findByPk.mockResolvedValue({
            email: null,
            notification_preferences: { inboxCreated: { email: true } },
        });
        await notifyInboxCreated(1, item);
        expect(enqueue).not.toHaveBeenCalled();
    });

    it('persists the exact original text and source UID', async () => {
        User.findByPk.mockResolvedValue({
            email: 'user@example.test',
            notification_preferences: { inboxCreated: { email: true } },
        });
        const transaction = { afterCommit: jest.fn() };
        await notifyInboxCreated(42, item, { transaction });

        expect(User.findByPk).toHaveBeenCalledWith(42, {
            attributes: ['email', 'notification_preferences'],
            transaction,
        });
        expect(enqueue).toHaveBeenCalledTimes(1);
        expect(enqueue).toHaveBeenCalledWith({
            userId: 42,
            kind: 'inbox',
            sourceUid: item.uid,
            recipient: 'user@example.test',
            subject: 'GTDdo: nuova voce Inbox',
            text: item.content,
            transaction,
        });
    });

    it('passes database failures through to the atomic capture transaction', async () => {
        User.findByPk.mockResolvedValue({
            email: 'user@example.test',
            notification_preferences: { inboxCreated: { email: true } },
        });
        enqueue.mockRejectedValue(new Error('Database unavailable'));
        await expect(notifyInboxCreated(1, item)).rejects.toThrow(
            'Database unavailable'
        );
    });
});
