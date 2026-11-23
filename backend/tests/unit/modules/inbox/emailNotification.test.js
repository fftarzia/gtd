'use strict';

jest.mock('../../../../services/emailService', () => ({
    isEmailEnabled: jest.fn(),
    sendEmail: jest.fn(),
}));
jest.mock('../../../../models', () => ({
    User: { findByPk: jest.fn() },
}));

const { User } = require('../../../../models');
const {
    isEmailEnabled,
    sendEmail,
} = require('../../../../services/emailService');
const {
    notifyInboxCreated,
} = require('../../../../modules/inbox/emailNotification');

describe('Inbox email notifications', () => {
    beforeEach(() => {
        jest.clearAllMocks();
        isEmailEnabled.mockReturnValue(true);
        sendEmail.mockResolvedValue({ success: true });
    });

    it('does nothing when SMTP is disabled', async () => {
        isEmailEnabled.mockReturnValue(false);
        await notifyInboxCreated(1, 'Capture this idea');
        expect(User.findByPk).not.toHaveBeenCalled();
        expect(sendEmail).not.toHaveBeenCalled();
    });

    it('does not send mail without opt-in', async () => {
        User.findByPk.mockResolvedValue({
            email: 'user@example.test',
            notification_preferences: { inboxCreated: { email: false } },
        });
        await notifyInboxCreated(1, 'Capture this idea');
        expect(sendEmail).not.toHaveBeenCalled();
    });

    it('does not send mail for an account without an address', async () => {
        User.findByPk.mockResolvedValue({
            email: null,
            notification_preferences: { inboxCreated: { email: true } },
        });
        await notifyInboxCreated(1, 'Capture this idea');
        expect(sendEmail).not.toHaveBeenCalled();
    });

    it('sends the exact Inbox text, including its line breaks', async () => {
        const content = 'Nota rapida per il cliente\nSeconda riga: prova #idee';
        User.findByPk.mockResolvedValue({
            email: 'user@example.test',
            notification_preferences: { inboxCreated: { email: true } },
        });
        await notifyInboxCreated(42, content);

        expect(User.findByPk).toHaveBeenCalledWith(42, {
            attributes: ['email', 'notification_preferences'],
        });
        expect(sendEmail).toHaveBeenCalledTimes(1);
        expect(sendEmail).toHaveBeenCalledWith({
            to: 'user@example.test',
            subject: 'GTDdo: nuova voce Inbox',
            text: content,
        });
    });

    it('records a delivery failure without throwing', async () => {
        User.findByPk.mockResolvedValue({
            email: 'user@example.test',
            notification_preferences: { inboxCreated: { email: true } },
        });
        sendEmail.mockResolvedValue({
            success: false,
            reason: 'SMTP unavailable',
        });
        const log = jest.spyOn(console, 'error').mockImplementation(() => {});
        try {
            await expect(notifyInboxCreated(1, 'Capture this idea')).resolves.toBeUndefined();
            expect(log).toHaveBeenCalledWith(
                'Could not deliver Inbox email notification:',
                'SMTP unavailable'
            );
        } finally {
            log.mockRestore();
        }
    });
});
