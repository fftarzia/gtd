'use strict';

const { DataTypes } = require('sequelize');

// Persistent, deduplicated outbox for notification emails. The original
// message is stored before delivery, so SMTP outages and restarts are safe.
module.exports = (sequelize) =>
    sequelize.define(
        'NotificationEmailDelivery',
        {
            id: {
                type: DataTypes.INTEGER,
                primaryKey: true,
                autoIncrement: true,
            },
            user_id: {
                type: DataTypes.INTEGER,
                allowNull: false,
                references: { model: 'users', key: 'id' },
            },
            dedup_key: {
                type: DataTypes.STRING(160),
                allowNull: false,
                unique: true,
            },
            kind: {
                type: DataTypes.STRING(24),
                allowNull: false,
            },
            source_uid: {
                type: DataTypes.STRING(64),
                allowNull: false,
            },
            recipient: {
                type: DataTypes.STRING(255),
                allowNull: false,
            },
            subject: {
                type: DataTypes.STRING(500),
                allowNull: false,
            },
            body_text: {
                type: DataTypes.TEXT,
                allowNull: true,
            },
            body_html: {
                type: DataTypes.TEXT,
                allowNull: true,
            },
            status: {
                type: DataTypes.STRING(16),
                allowNull: false,
                defaultValue: 'pending',
            },
            attempts: {
                type: DataTypes.INTEGER,
                allowNull: false,
                defaultValue: 0,
            },
            next_attempt_at: {
                type: DataTypes.DATE,
                allowNull: true,
            },
            last_error: {
                type: DataTypes.STRING(500),
                allowNull: true,
            },
            sent_at: {
                type: DataTypes.DATE,
                allowNull: true,
            },
        },
        {
            tableName: 'notification_email_deliveries',
            indexes: [
                {
                    fields: ['status', 'next_attempt_at'],
                    name: 'notification_email_deliveries_status_next',
                },
                {
                    fields: ['user_id', 'created_at'],
                    name: 'notification_email_deliveries_user_created',
                },
            ],
        }
    );
