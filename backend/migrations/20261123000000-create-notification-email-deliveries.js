'use strict';

// Purely additive. Existing data and SMTP configuration are untouched.
// On a fresh database, Sequelize creates this table from the model instead.
module.exports = {
    async up(queryInterface, Sequelize) {
        await queryInterface.createTable('notification_email_deliveries', {
            id: {
                type: Sequelize.INTEGER,
                primaryKey: true,
                autoIncrement: true,
            },
            user_id: {
                type: Sequelize.INTEGER,
                allowNull: false,
                references: { model: 'users', key: 'id' },
                onUpdate: 'CASCADE',
                onDelete: 'CASCADE',
            },
            dedup_key: {
                type: Sequelize.STRING(160),
                allowNull: false,
                unique: true,
            },
            kind: {
                type: Sequelize.STRING(24),
                allowNull: false,
            },
            source_uid: {
                type: Sequelize.STRING(64),
                allowNull: false,
            },
            recipient: {
                type: Sequelize.STRING(255),
                allowNull: false,
            },
            subject: {
                type: Sequelize.STRING(500),
                allowNull: false,
            },
            body_text: {
                type: Sequelize.TEXT,
                allowNull: true,
            },
            body_html: {
                type: Sequelize.TEXT,
                allowNull: true,
            },
            status: {
                type: Sequelize.STRING(16),
                allowNull: false,
                defaultValue: 'pending',
            },
            attempts: {
                type: Sequelize.INTEGER,
                allowNull: false,
                defaultValue: 0,
            },
            next_attempt_at: {
                type: Sequelize.DATE,
                allowNull: true,
            },
            last_error: {
                type: Sequelize.STRING(500),
                allowNull: true,
            },
            sent_at: {
                type: Sequelize.DATE,
                allowNull: true,
            },
            created_at: {
                type: Sequelize.DATE,
                allowNull: false,
                defaultValue: Sequelize.literal('CURRENT_TIMESTAMP'),
            },
            updated_at: {
                type: Sequelize.DATE,
                allowNull: false,
                defaultValue: Sequelize.literal('CURRENT_TIMESTAMP'),
            },
        });
        await queryInterface.addIndex(
            'notification_email_deliveries',
            ['status', 'next_attempt_at'],
            { name: 'notification_email_deliveries_status_next' }
        );
        await queryInterface.addIndex(
            'notification_email_deliveries',
            ['user_id', 'created_at'],
            { name: 'notification_email_deliveries_user_created' }
        );
    },

    async down(queryInterface) {
        await queryInterface.dropTable('notification_email_deliveries');
    },
};
