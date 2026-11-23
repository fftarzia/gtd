'use strict';

const {
    shouldSendEmailNotification,
} = require('../../utils/notificationPreferences');

// The Inbox is a capture space, not a task notification. Send its exact
// text as plain text (preserving line breaks), only for users who opt in.
// This runs after an item has been saved and must never undo a capture.
async function notifyInboxCreated(userId, content) {
    const {
        isEmailEnabled,
        sendEmail,
    } = require('../../services/emailService');
    if (!isEmailEnabled()) return;

    const { User } = require('../../models');
    const user = await User.findByPk(userId, {
        attributes: ['email', 'notification_preferences'],
    });
    if (!user?.email || !shouldSendEmailNotification(user, 'inboxCreated')) {
        return;
    }

    const result = await sendEmail({
        to: user.email,
        subject: 'GTDdo: nuova voce Inbox',
        text: content,
    });

    if (!result.success) {
        console.error(
            'Could not deliver Inbox email notification:',
            result.reason
        );
    }
}

module.exports = { notifyInboxCreated };
