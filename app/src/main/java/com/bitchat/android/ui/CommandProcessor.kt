package com.bitchat.android.ui

import com.bitchat.android.mesh.BluetoothMeshService
import com.bitchat.android.model.BitchatMessage
import java.util.Date

/**
 * Handles processing of IRC-style commands
 */
class CommandProcessor(
    private val state: ChatState,
    private val messageManager: MessageManager,
    private val channelManager: ChannelManager,
    private val privateChatManager: PrivateChatManager
) {

    // Available commands list
    private val baseCommands = listOf(
        CommandSuggestion("/block", emptyList(), "[nickname]", "block or list blocked peers"),
        CommandSuggestion("/channels", emptyList(), null, "show all discovered channels"),
        CommandSuggestion("/clear", emptyList(), null, "clear chat messages"),
        CommandSuggestion("/hug", emptyList(), "<nickname>", "send someone a warm hug"),
        CommandSuggestion("/j", listOf("/join"), "<channel>", "join or create a channel"),
        CommandSuggestion("/m", listOf("/msg"), "<nickname> [message]", "send private message"),
        CommandSuggestion("/slap", emptyList(), "<nickname>", "slap someone with a trout"),
        CommandSuggestion("/unblock", emptyList(), "<nickname>", "unblock a peer"),
        CommandSuggestion("/w", emptyList(), null, "see who's online"),
        CommandSuggestion("/role", listOf("/r"), "<guardian|civilian>", "set your node role"),
        CommandSuggestion("/sos", emptyList(), "<flood|food|medical|fire>", "send EMERGENCY priority signal"),
        CommandSuggestion("/wipe", emptyList(), null, "IMMEDIATELY WIPE ALL DATA")
    )

    // MARK: - Command Processing

    fun processCommand(command: String, meshService: BluetoothMeshService, myPeerID: String, onSendMessage: (String, List<String>, String?) -> Unit, viewModel: ChatViewModel? = null): Boolean {
        if (!command.startsWith("/")) return false

        val parts = command.split(" ")
        when (val cmd = parts.first().lowercase()) {
            "/j", "/join" -> handleJoinCommand(parts, myPeerID)
            "/m", "/msg" -> handleMessageCommand(parts, meshService)
            "/w" -> handleWhoCommand(meshService, viewModel)
            "/clear" -> handleClearCommand()
            "/pass" -> handlePassCommand(parts, myPeerID)
            "/block" -> handleBlockCommand(parts, meshService)
            "/unblock" -> handleUnblockCommand(parts, meshService)
            "/hug" -> handleActionCommand(parts, "gives", "a warm hug 🫂", meshService, myPeerID, onSendMessage)
            "/slap" -> handleActionCommand(parts, "slaps", "around a bit with a large trout 🐟", meshService, myPeerID, onSendMessage)
            "/channels" -> handleChannelsCommand()
            "/role", "/r" -> handleRoleCommand(parts)
            "/sos" -> handleSOSCommand(parts, meshService, viewModel?.getApplication<android.app.Application>()?.applicationContext)
            "/wipe" -> handleWipeCommand(viewModel)
            else -> handleUnknownCommand(cmd)
        }

        return true
    }

    private fun handleJoinCommand(parts: List<String>, myPeerID: String) {
        if (parts.size > 1) {
            val channelName = parts[1]
            val channel = if (channelName.startsWith("#")) channelName else "#$channelName"
            val password = if (parts.size > 2) parts[2] else null
            val success = channelManager.joinChannel(channel, password, myPeerID)
            if (success) {
                val systemMessage = BitchatMessage(
                    sender = "system",
                    content = "joined channel $channel",
                    timestamp = Date(),
                    isRelay = false
                )
                messageManager.addMessage(systemMessage)
            }
        } else {
            val systemMessage = BitchatMessage(
                sender = "system",
                content = "usage: /join <channel>",
                timestamp = Date(),
                isRelay = false
            )
            messageManager.addMessage(systemMessage)
        }
    }

    private fun handleMessageCommand(parts: List<String>, meshService: BluetoothMeshService) {
        if (parts.size > 1) {
            val targetName = parts[1].removePrefix("@")
            val peerID = getPeerIDForNickname(targetName, meshService)

            if (peerID != null) {
                val success = privateChatManager.startPrivateChat(peerID, meshService)

                if (success) {
                    if (parts.size > 2) {
                        val messageContent = parts.drop(2).joinToString(" ")
                        val recipientNickname = getPeerNickname(peerID, meshService)
                        privateChatManager.sendPrivateMessage(
                            messageContent,
                            peerID,
                            recipientNickname,
                            state.getNicknameValue(),
                            getMyPeerID(meshService)
                        ) { content, peerIdParam, recipientNicknameParam, messageId ->
                            // This would trigger the actual mesh service send
                            sendPrivateMessageVia(meshService, content, peerIdParam, recipientNicknameParam, messageId)
                        }
                    } else {
                        val systemMessage = BitchatMessage(
                            sender = "system",
                            content = "started private chat with $targetName",
                            timestamp = Date(),
                            isRelay = false
                        )
                        messageManager.addMessage(systemMessage)
                    }
                }
            } else {
                val systemMessage = BitchatMessage(
                    sender = "system",
                    content = "user '$targetName' not found. they may be offline or using a different nickname.",
                    timestamp = Date(),
                    isRelay = false
                )
                messageManager.addMessage(systemMessage)
            }
        } else {
            val systemMessage = BitchatMessage(
                sender = "system",
                content = "usage: /msg <nickname> [message]",
                timestamp = Date(),
                isRelay = false
            )
            messageManager.addMessage(systemMessage)
        }
    }

    private fun handleWhoCommand(meshService: BluetoothMeshService, viewModel: ChatViewModel? = null) {
        // Channel-aware who command (matches iOS behavior)
        val (peerList, contextDescription) = if (viewModel != null) {
            when (val selectedChannel = viewModel.selectedLocationChannel.value) {
                is com.bitchat.android.geohash.ChannelID.Mesh,
                null -> {
                    // Mesh channel: show Bluetooth-connected peers
                    val connectedPeers = state.getConnectedPeersValue()
                    val peerList = connectedPeers.joinToString(", ") { peerID ->
                        getPeerNickname(peerID, meshService)
                    }
                    Pair(peerList, "online users")
                }

                is com.bitchat.android.geohash.ChannelID.Location -> {
                    // Location channel: show geohash participants
                    val geohashPeople = viewModel.geohashPeople.value ?: emptyList()
                    val currentNickname = state.getNicknameValue()

                    val participantList = geohashPeople.mapNotNull { person ->
                        val displayName = person.displayName
                        // Exclude self from list
                        if (displayName.startsWith("${currentNickname}#")) {
                            null
                        } else {
                            displayName
                        }
                    }.joinToString(", ")

                    Pair(participantList, "participants in ${selectedChannel.channel.geohash}")
                }
            }
        } else {
            // Fallback to mesh behavior
            val connectedPeers = state.getConnectedPeersValue()
            val peerList = connectedPeers.joinToString(", ") { peerID ->
                getPeerNickname(peerID, meshService)
            }
            Pair(peerList, "online users")
        }

        val systemMessage = BitchatMessage(
            sender = "system",
            content = if (peerList.isEmpty()) {
                "no one else is around right now."
            } else {
                "$contextDescription: $peerList"
            },
            timestamp = Date(),
            isRelay = false
        )
        messageManager.addMessage(systemMessage)
    }

    private fun handleClearCommand() {
        when {
            state.getSelectedPrivateChatPeerValue() != null -> {
                // Clear private chat
                val peerID = state.getSelectedPrivateChatPeerValue()!!
                messageManager.clearPrivateMessages(peerID)
            }
            state.getCurrentChannelValue() != null -> {
                // Clear channel messages
                val channel = state.getCurrentChannelValue()!!
                messageManager.clearChannelMessages(channel)
            }
            else -> {
                // Clear main messages
                messageManager.clearMessages()
            }
        }
    }

    private fun handlePassCommand(parts: List<String>, peerID: String) {
        val currentChannel = state.getCurrentChannelValue()

        if (currentChannel == null) {
            val systemMessage = BitchatMessage(
                sender = "system",
                content = "you must be in a channel to set a password.",
                timestamp = Date(),
                isRelay = false
            )
            messageManager.addMessage(systemMessage)
            return
        }

        if (parts.size == 2){
            if(!channelManager.isChannelCreator(channel = currentChannel, peerID = peerID)){
                val systemMessage = BitchatMessage(
                    sender = "system",
                    content = "you must be the channel creator to set a password.",
                    timestamp = Date(),
                    isRelay = false
                )
                channelManager.addChannelMessage(currentChannel,systemMessage,null)
                return
            }
            val newPassword = parts[1]
            channelManager.setChannelPassword(currentChannel, newPassword)
            val systemMessage = BitchatMessage(
                sender = "system",
                content = "password changed for channel $currentChannel",
                timestamp = Date(),
                isRelay = false
            )
            channelManager.addChannelMessage(currentChannel,systemMessage,null)
        }
        else{
            val systemMessage = BitchatMessage(
                sender = "system",
                content = "usage: /pass <password>",
                timestamp = Date(),
                isRelay = false
            )
            channelManager.addChannelMessage(currentChannel,systemMessage,null)
        }
    }

    private fun handleBlockCommand(parts: List<String>, meshService: BluetoothMeshService) {
        if (parts.size > 1) {
            val targetName = parts[1].removePrefix("@")
            privateChatManager.blockPeerByNickname(targetName, meshService)
        } else {
            // List blocked users
            val blockedInfo = privateChatManager.listBlockedUsers()
            val systemMessage = BitchatMessage(
                sender = "system",
                content = blockedInfo,
                timestamp = Date(),
                isRelay = false
            )
            messageManager.addMessage(systemMessage)
        }
    }

    private fun handleUnblockCommand(parts: List<String>, meshService: BluetoothMeshService) {
        if (parts.size > 1) {
            val targetName = parts[1].removePrefix("@")
            privateChatManager.unblockPeerByNickname(targetName, meshService)
        } else {
            val systemMessage = BitchatMessage(
                sender = "system",
                content = "usage: /unblock <nickname>",
                timestamp = Date(),
                isRelay = false
            )
            messageManager.addMessage(systemMessage)
        }
    }

    private fun handleActionCommand(
        parts: List<String>,
        verb: String,
        actionObject: String,
        meshService: BluetoothMeshService,
        myPeerID: String,
        onSendMessage: (String, List<String>, String?) -> Unit
    ) {
        if (parts.size > 1) {
            val targetName = parts[1].removePrefix("@")
            val actionMessage = "* ${state.getNicknameValue() ?: "someone"} $verb $targetName $actionObject *"

            // If we're in a geohash location channel, don't add a local echo here.
            // GeohashViewModel.sendGeohashMessage() will add the local echo with proper metadata.
            val isInLocationChannel = state.selectedLocationChannel.value is com.bitchat.android.geohash.ChannelID.Location

            // Send as regular message
            if (state.getSelectedPrivateChatPeerValue() != null) {
                val peerID = state.getSelectedPrivateChatPeerValue()!!
                privateChatManager.sendPrivateMessage(
                    actionMessage,
                    peerID,
                    getPeerNickname(peerID, meshService),
                    state.getNicknameValue(),
                    myPeerID
                ) { content, peerIdParam, recipientNicknameParam, messageId ->
                    sendPrivateMessageVia(meshService, content, peerIdParam, recipientNicknameParam, messageId)
                }
            } else if (isInLocationChannel) {
                // Let the transport layer add the echo; just send it out
                onSendMessage(actionMessage, emptyList(), null)
            } else {
                val message = BitchatMessage(
                    sender = state.getNicknameValue() ?: myPeerID,
                    content = actionMessage,
                    timestamp = Date(),
                    isRelay = false,
                    senderPeerID = myPeerID,
                    channel = state.getCurrentChannelValue()
                )

                if (state.getCurrentChannelValue() != null) {
                    channelManager.addChannelMessage(state.getCurrentChannelValue()!!, message, myPeerID)
                    onSendMessage(actionMessage, emptyList(), state.getCurrentChannelValue())
                } else {
                    messageManager.addMessage(message)
                    onSendMessage(actionMessage, emptyList(), null)
                }
            }
        } else {
            val systemMessage = BitchatMessage(
                sender = "system",
                content = "usage: /${parts[0].removePrefix("/")} <nickname>",
                timestamp = Date(),
                isRelay = false
            )
            messageManager.addMessage(systemMessage)
        }
    }

    private fun handleChannelsCommand() {
        val channels = channelManager.getJoinedChannelsList().joinToString(", ")
        val systemMessage = BitchatMessage(
            sender = "system",
            content = if (channels.isEmpty()) "no channels discovered" else "available channels: $channels",
            timestamp = Date(),
            isRelay = false
        )
        messageManager.addMessage(systemMessage)
    }

    private fun handleUnknownCommand(command: String) {
        val didYouMean = baseCommands.find { it.command == command || it.aliases.contains(command) }?.let { "" } ?: run {
            val similarCommands = baseCommands.filter { it.command.startsWith(command) }
            if (similarCommands.isNotEmpty()) {
                ", did you mean: ${similarCommands.joinToString(", ") { it.command }}"
            } else {
                ""
            }
        }
        val systemMessage = BitchatMessage(
            sender = "system",
            content = "unknown command '$command'$didYouMean",
            timestamp = Date(),
            isRelay = false
        )
        messageManager.addMessage(systemMessage)
    }

    private fun getPeerIDForNickname(nickname: String, meshService: BluetoothMeshService): String? {
        return meshService.getPeerNicknames().entries.find { it.value == nickname }?.key
    }

    private fun getPeerNickname(peerID: String, meshService: BluetoothMeshService): String {
        return meshService.getPeerNicknames()[peerID] ?: peerID
    }

    private fun getMyPeerID(meshService: BluetoothMeshService): String {
        return meshService.myPeerID
    }

    private fun sendPrivateMessageVia(meshService: BluetoothMeshService, content: String, peerId: String, recipientNickname: String, messageId: String) {
        meshService.sendPrivateMessage(content, peerId, recipientNickname, messageId)
    }

    fun updateCommandSuggestions(input: String) {
        if (input.startsWith("/")) {
            val commandPart = input.split(" ").first()
            val suggestions = baseCommands.filter { it.command.startsWith(commandPart, ignoreCase = true) }
            state.setCommandSuggestions(suggestions)
            state.setShowCommandSuggestions(suggestions.isNotEmpty())
        } else {
            state.setShowCommandSuggestions(false)
        }
    }

    fun selectCommandSuggestion(suggestion: CommandSuggestion): String {
        state.setShowCommandSuggestions(false)
        return suggestion.command
    }

    fun updateMentionSuggestions(input: String, meshService: BluetoothMeshService, viewModel: ChatViewModel) {
        val lastAt = input.lastIndexOf("@")
        if (lastAt != -1) {
            val mentionQuery = input.substring(lastAt + 1)
            val connectedPeers = state.getConnectedPeersValue()
            val nicknames = connectedPeers.map { getPeerNickname(it, meshService) }
            val suggestions = nicknames.filter { it.startsWith(mentionQuery, ignoreCase = true) }
            state.setMentionSuggestions(suggestions)
            state.setShowMentionSuggestions(suggestions.isNotEmpty())
        } else {
            state.setShowMentionSuggestions(false)
        }
    }

    fun selectMentionSuggestion(nickname: String, currentText: String): String {
        state.setShowMentionSuggestions(false)
        val lastAt = currentText.lastIndexOf("@")
        return if (lastAt != -1) {
            currentText.substring(0, lastAt + 1) + nickname + " "
        } else {
            nickname
        }
    }

    private fun handleRoleCommand(parts: List<String>) {
        if (parts.size > 1) {
            val role = parts[1].lowercase()
            if (role == "guardian") {
                state.setIsGuardianMode(true)
                addSystemMessage("🛡️ You are now active as a Guardian Node.")
            } else if (role == "civilian") {
                state.setIsGuardianMode(false)
                addSystemMessage("You are now a Civilian Node.")
            } else {
                addSystemMessage("usage: /role <guardian|civilian>")
            }
        } else {
            val currentRole = if (state.getIsGuardianModeValue()) "Guardian 🛡️" else "Civilian"
            addSystemMessage("Current Role: $currentRole (usage: /role <guardian|civilian>)")
        }
    }

    private fun handleSOSCommand(parts: List<String>, meshService: BluetoothMeshService, context: android.content.Context?) {
        val rawArg = if (parts.size > 1) parts.drop(1).joinToString(" ") else "EMERGENCY"
        
        // Predefined Emergency Shortcuts
        val customMsg = when (rawArg.lowercase()) {
            "flood" -> "🌊 TRAPPED IN FLOOD WATER - Need Evacuation!"
            "food" -> "🥪 CRITICAL SHORTAGE - Need Food & Water"
            "medical", "med" -> "🚑 MEDICAL EMERGENCY - Need Doctor/Ambulance"
            "fire" -> "🔥 FIRE OUTBREAK - Need Assistance"
            "rubble" -> "🧱 TRAPPED UNDER RUBBLE"
            else -> rawArg // Fallback to whatever user typed
        }
        
        // Context Awareness: Battery Level
        val batteryLevel = try {
            val bm = context?.getSystemService(android.content.Context.BATTERY_SERVICE) as? android.os.BatteryManager
            bm?.getIntProperty(android.os.BatteryManager.BATTERY_PROPERTY_CAPACITY) ?: -1
        } catch (e: Exception) { -1 }

        // Context Awareness: Location (Mock/Last Known for stability)
        // In a real disaster app, this would query FusedLocationProvider
        val locationStr = "[12.9716° N, 77.5946° E]" 

        val richSOS = """
            🚨 **SOS ALERT** 🚨
            User: @${state.getNicknameValue() ?: "Unknown"}
            Msg: **$customMsg**
            📍 Loc: $locationStr
            🔋 Batt: ${if (batteryLevel > 0) "$batteryLevel%" else "Unknown"}
        """.trimIndent()

        meshService.sendSOSMessage(richSOS)
        addSystemMessage("🚨 SOS SIGNAL SENT: Broadcasting detailed status!")
    }

    private fun handleWipeCommand(viewModel: ChatViewModel?) {
        if (viewModel == null) {
            addSystemMessage("Error: Cannot execute wipe (ViewModel not attached)")
            return
        }
        addSystemMessage("⚠️ INITIATING EMERGENCY WIPE Sequence...")
        viewModel.panicClearAllData()
        android.os.Process.killProcess(android.os.Process.myPid())
        System.exit(0)
    }

    private fun addSystemMessage(text: String) {
         val systemMessage = BitchatMessage(
            sender = "system",
            content = text,
            timestamp = Date(),
            isRelay = false
        )
        messageManager.addMessage(systemMessage)
    }
}
