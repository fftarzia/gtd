'use strict';

const { Op, literal } = require('sequelize');
const { NotificationEmailDelivery, Notification } = require('../models');
const { isEmailEnabled, sendEmail } = require('./emailService');
const { logError } = require('./logService');

const MAX_ATTEMPTS = 8;
const RETRY_DELAYS_MIN = [1, 5, 15, 30, 60, 120, 240];
const STUCK_SENDING_MS = 20 * 60 * 1000;
const KEEP_SENT_DAYS = 7;
const KEEP_FAILED_DAYS = 30;
const IMMEDIATE = process.env.NODE_ENV !== 'test';

function schedule(deliveryId) {
    if (!IMMEDIATE) return;
    setImmediate(() => {
        deliver(deliveryId).catch((error) => {
            logError(error, 'Failed to process queued notification email');
        });
    });
}

// The unique source key prevents a notification from being queued twice.
// When a transaction is passed, the message is committed atomically with the
// event and will only be sent once that transaction has committed.
async function enqueue({
    userId,
    kind,
    sourceUid,
    recipient,
    subject,
    text = null,
    html = null,
    transaction = null,
}) {
    if (!isEmailEnabled()) return null;

    const [delivery, created] = await NotificationEmailDelivery.findOrCreate({
        where: { dedup_key: kind + ':' + sourceUid },
        defaults: {
            user_id: userId,
            kind,
            source_uid: sourceUid,
            recipient,
            subject,
            body_text: text,
            body_html: html,
            status: 'pending',
            next_attempt_at: new Date(),
        },
        ...(transaction ? { transaction } : {}),
    });

    if (created) {
        if (transaction) {
            transaction.afterCommit(() => schedule(delivery.id));
        } else {
            schedule(delivery.id);
        }
    }
    return delivery;
}

async function markNotificationSent(delivery) {
    if (delivery.kind !== 'notification') return;
    try {
        const notification = await Notification.findOne({
            where: {
                uid: delivery.source_uid,
                user_id: delivery.user_id,
            },
        });
        if (notification) await notification.markChannelAsSent('email');
    } catch (error) {
        // The message is already accepted by SMTP: never resend it just
        // because the optional notification timestamp could not be saved.
        logError(error, 'Failed to mark notification email as delivered');
    }
}

// Atomic claim prevents the immediate sender and cron (or two app replicas)
// from processing a row concurrently.
async function deliver(deliveryId, { now = new Date() } = {}) {
    const [claimed] = await NotificationEmailDelivery.update(
        { status: 'sending', attempts: literal('attempts + 1') },
        {
            where: {
                id: deliveryId,
                status: 'pending',
                next_attempt_at: { [Op.lte]: now },
            },
        }
    );
    if (!claimed) return null;

    const delivery = await NotificationEmailDelivery.findByPk(deliveryId);
    let result;
    try {
        result = await sendEmail({
            to: delivery.recipient,
            subject: delivery.subject,
            text: delivery.body_text,
            html: delivery.body_html,
        });
    } catch (error) {
        result = {
            success: false,
            reason: error.message || 'SMTP delivery failed',
        };
    }

    if (result.success) {
        await delivery.update({
            status: 'sent',
            sent_at: new Date(),
            next_attempt_at: null,
            last_error: null,
        });
        await markNotificationSent(delivery);
        return delivery;
    }

    const attempts = delivery.attempts;
    const exhausted = attempts >= MAX_ATTEMPTS;
    await delivery.update({
        status: exhausted ? 'failed' : 'pending',
        next_attempt_at: exhausted
            ? null
            : new Date(
                  now.getTime() + RETRY_DELAYS_MIN[attempts - 1] * 60 * 1000
              ),
        last_error: String(result.reason || 'SMTP delivery failed').slice(
            0,
            500
        ),
    });
    if (exhausted) {
        console.warn(
            'Notification email delivery ' +
                delivery.id +
                ' failed after ' +
                attempts +
                ' attempts'
        );
    }
    return delivery;
}

// Run every minute by the existing task scheduler; no new worker/container.
// A crashed sending process can leave a row leased, so release stale rows.
async function processDue({ now = new Date(), limit = 100 } = {}) {
    const stuckBefore = new Date(now.getTime() - STUCK_SENDING_MS);
    await NotificationEmailDelivery.update(
        { status: 'pending', next_attempt_at: now },
        {
            where: {
                status: 'sending',
                updated_at: { [Op.lt]: stuckBefore },
                attempts: { [Op.lt]: MAX_ATTEMPTS },
            },
        }
    );
    await NotificationEmailDelivery.update(
        {
            status: 'failed',
            next_attempt_at: null,
            last_error: 'Interrupted during final delivery attempt',
        },
        {
            where: {
                status: 'sending',
                updated_at: { [Op.lt]: stuckBefore },
                attempts: { [Op.gte]: MAX_ATTEMPTS },
            },
        }
    );

    const due = await NotificationEmailDelivery.findAll({
        where: {
            status: 'pending',
            next_attempt_at: { [Op.lte]: now },
        },
        attributes: ['id'],
        order: [
            ['next_attempt_at', 'ASC'],
            ['id', 'ASC'],
        ],
        limit,
    });

    let sent = 0;
    for (const row of due) {
        const delivery = await deliver(row.id, { now });
        if (delivery?.status === 'sent') sent += 1;
    }

    const dayMs = 24 * 60 * 60 * 1000;
    await NotificationEmailDelivery.destroy({
        where: {
            status: 'sent',
            created_at: {
                [Op.lt]: new Date(now.getTime() - KEEP_SENT_DAYS * dayMs),
            },
        },
    });
    await NotificationEmailDelivery.destroy({
        where: {
            status: 'failed',
            created_at: {
                [Op.lt]: new Date(now.getTime() - KEEP_FAILED_DAYS * dayMs),
            },
        },
    });

    return { processed: due.length, sent };
}

module.exports = {
    enqueue,
    deliver,
    processDue,
    MAX_ATTEMPTS,
    RETRY_DELAYS_MIN,
    STUCK_SENDING_MS,
};
