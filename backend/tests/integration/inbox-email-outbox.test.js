'use strict';

jest.mock('../../services/emailService', () => ({
    isEmailEnabled: jest.fn(() => true),
    sendEmail: jest.fn(),
}));

const bcrypt = require('bcrypt');
const { User, InboxItem, NotificationEmailDelivery } = require('../../models');
const inboxService = require('../../modules/inbox/service');
const { sendEmail } = require('../../services/emailService');

describe('Inbox atomic email outbox', () => {
    let user;

    beforeEach(async () => {
        user = await User.create({
            email: 'inbox-outbox@example.test',
            password_digest: await bcrypt.hash('example-password', 10),
            notification_preferences: { inboxCreated: { email: true } },
        });
    });

    it('commits the Inbox note and its verbatim email together', async () => {
        const content = 'Testo originale\nSeconda riga con #tag';
        const item = await inboxService.create(user.id, {
            content,
            source: 'web',
        });
        const saved = await InboxItem.findOne({ where: { uid: item.uid } });
        const delivery = await NotificationEmailDelivery.findOne({
            where: { dedup_key: 'inbox:' + item.uid },
        });

        expect(saved.content).toBe(content);
        expect(delivery).not.toBeNull();
        expect(delivery.body_text).toBe(content);
        expect(delivery.recipient).toBe(user.email);
        expect(delivery.status).toBe('pending');
        expect(delivery.attempts).toBe(0);
        expect(sendEmail).not.toHaveBeenCalled();
    });

    it('rolls back both records if the local outbox write fails', async () => {
        const failure = jest
            .spyOn(NotificationEmailDelivery, 'findOrCreate')
            .mockRejectedValueOnce(new Error('Outbox write failed'));

        try {
            await expect(
                inboxService.create(user.id, {
                    content: 'Should not be partially saved',
                    source: 'web',
                })
            ).rejects.toThrow('Outbox write failed');

            const remaining = await InboxItem.count({
                where: { user_id: user.id },
            });
            expect(remaining).toBe(0);
            expect(
                await NotificationEmailDelivery.count({
                    where: { user_id: user.id },
                })
            ).toBe(0);
        } finally {
            failure.mockRestore();
        }
    });

    it('does not enqueue an email when the user has opted out', async () => {
        await user.update({
            notification_preferences: { inboxCreated: { email: false } },
        });
        await inboxService.create(user.id, {
            content: 'An ordinary Inbox capture',
            source: 'web',
        });

        const captured = await InboxItem.count({
            where: { user_id: user.id },
        });
        expect(captured).toBe(1);
        expect(
            await NotificationEmailDelivery.count({
                where: { user_id: user.id },
            })
        ).toBe(0);
    });
});
