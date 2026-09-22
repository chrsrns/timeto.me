package me.timeto.shared

import kotlinx.coroutines.channels.Channel

class TestDialogsManager(
    val alerts: Channel<String> = Channel(Channel.UNLIMITED),
    val confirmations: Channel<Pair<String, () -> Unit>> = Channel(Channel.UNLIMITED),
) : DialogsManager {

    override fun alert(message: String) {
        alerts.trySend(message)
    }

    override fun confirmation(
        message: String,
        buttonText: String,
        onConfirm: () -> Unit,
    ) {
        confirmations.trySend(message to onConfirm)
    }
}
