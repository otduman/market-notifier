package com.dumanch1.marketnotifier.notification;

/**
 * Abstraction for delivering alert messages to external channels.
 *
 * Implementations may target Telegram, Discord, Slack, email, etc.
 * AlertService depends on this interface (via Optional injection),
 * so adding a new channel is just a new implementation — no changes
 * to the alert logic itself.
 */
public interface NotificationService {

    /**
     * Sends an alert message to the configured channel.
     * Implementations must be fault-tolerant — a failed send must never
     * propagate an exception to the caller.
     *
     * @param message the formatted alert text to deliver
     */
    void send(String message);
}
