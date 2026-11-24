'use strict';

const {
    shouldSendEmailNotification,
} = require('../../utils/notificationPreferences');
const { isEmailEnabled } = require('../../services/emailService');
const { enqueue } = require('../../services/notificationEmailQueue');
const { User } = require('../../models');

// Store the exact Inbox text as a plain-text email before the capture commits.
// SMTP delivery runs after commit, separately from the user's quick capture.
async function notifyInboxCreated(userId, item, { transaction } = {}) {
    if (!isEmailEnabled()) return null;

    const user = await User.findByPk(userId, {
        attributes: ['email', 'notification_preferences'],
        ...(transaction ? { transaction } : {}),
    });
    if (!user?.email || !shouldSendEmailNotification(user, 'inboxCreated')) {
        return null;
    }

    return enqueue({
        userId,
        kind: 'inbox',
        sourceUid: item.uid,
        recipient: user.email,
        subject: 'GTDdo: nuova voce Inbox',
        text: item.content,
        transaction,
    });
}

module.exports = { notifyInboxCreated };
