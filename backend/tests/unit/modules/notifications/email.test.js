'use strict';

jest.mock('../../../../services/emailService', () => ({
    isEmailEnabled: jest.fn(),
    hasValidEmailConfig: jest.fn(),
    sendEmail: jest.fn(),
}));
jest.mock('../../../../models', () => ({
    User: { findByPk: jest.fn() },
}));

const { User } = require('../../../../models');
const {
    isEmailEnabled,
    hasValidEmailConfig,
    sendEmail,
} = require('../../../../services/emailService');
const notificationsService = require('../../../../modules/notifications/service');

describe('SMTP notification service', () => {
    beforeEach(() => {
        jest.clearAllMocks();
        isEmailEnabled.mockReturnValue(true);
        hasValidEmailConfig.mockReturnValue(true);
        sendEmail.mockResolvedValue({ success: true });
        User.findByPk.mockResolvedValue({ email: 'user@example.test' });
    });

    it('does not report SMTP as configured if email is disabled', () => {
        isEmailEnabled.mockReturnValue(false);
        expect(notificationsService.getEmailStatus()).toEqual({ available: false });
    });

    it('does not report SMTP as configured if required fields are missing', () => {
        hasValidEmailConfig.mockReturnValue(false);
        expect(notificationsService.getEmailStatus()).toEqual({ available: false });
    });

    it('sends the test email only to the signed-in user', async () => {
        await expect(
            notificationsService.triggerTestNotification(17, 'email')
        ).resolves.toEqual({ channel: 'email', delivered: true });
        expect(User.findByPk).toHaveBeenCalledWith(17, {
            attributes: ['email'],
        });
        expect(sendEmail).toHaveBeenCalledWith({
            to: 'user@example.test',
            subject: 'GTDdo: test notifiche email',
            text: 'Le notifiche email di GTDdo funzionano.',
        });
    });

    it('rejects test requests before SMTP is configured', async () => {
        isEmailEnabled.mockReturnValue(false);
        await expect(
            notificationsService.triggerTestNotification(17, 'email')
        ).rejects.toThrow('Configure SMTP before testing');
        expect(sendEmail).not.toHaveBeenCalled();
    });

    it('returns delivery errors to the UI', async () => {
        sendEmail.mockResolvedValue({ success: false, reason: 'Connection refused' });
        await expect(
            notificationsService.triggerTestNotification(17, 'email')
        ).rejects.toThrow('Connection refused');
    });
});
