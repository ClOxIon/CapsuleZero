package com.capsulezero.game.core.gameActions

import com.capsulezero.game.core.*


//TODO: because of a no interrupt polity, the trade action should be executed immediately, not after the current action is finished.
//The player character accepts the trade if the value of the offered item is higher than the value of the requested item.
//The player cannot affect this decision.
class trade(targetState: GameState, targetCharacter: String, targetPlace: String) : GameAction(targetState, targetCharacter,
    targetPlace
) {
    var who = ""
    var item  = hashMapOf<String, Int>()
    var item2  = hashMapOf<String, Int>()
    var action : Command? = null
    var action2 : Command? = null
    var info : Information? = null
    var info2 : Information? = null
    var onFinished : (Boolean)->Unit = {} //This is called when the trade is accepted or rejected.
    override fun chooseParams() {
        who =
            tgtState.ongoingMeetings.filter {it.value.currentCharacters.contains(tgtCharacter)}.flatMap { it.value.currentCharacters }.first {it!=tgtCharacter}//Trade can happen only when there is exactly one other character in the meeting.

//        println("What do you want to trade?")
//        val type = GameEngine.acquire(listOf("resource", "information", "action"))
//        when (type){
//            "resource"->{
//                //TODO: Can only request information that you know the existence.
//                item = GameEngine.acquire(tgtState.characters[tgtCharacter]!!.resources.keys.toList())
//                amount = GameEngine.acquire(arrayListOf("1","2","3","4","5","6","7","8","9","10")).toInt()
//            }
//            "action"-> {
//                //println("When do you want the action to be executed?") TODO: this is not implemented yet.
//                //val meetingTime = tgtState.time+GameEngine.acquire(arrayListOf("3","6","9","12","18","21","24")).toInt()
//                println("Where do you want the action to be executed?")
//                val where = GameEngine.acquire(tgtState.places.map { it.value.name })
//                println("What action do you want to execute?")
//                val name = GameEngine.acquire(GameEngine.availableActions(tgtState, where, who))
//                action = Command(where, name, 0)
//            }
//            "information"->{
//                info = tgtState.informations[GameEngine.acquire(tgtState.informations.filter { it.value.knownTo.contains(tgtCharacter) }.map { it.key })] //You can give information that opponent has no clue about.
//            }
//        }
//        println("What do you want to trade it for?")
//        val type2 = GameEngine.acquire(listOf("resource", "information", "action"))
//        when (type2){
//            "resource"->{
//                //TODO: Can only request resource that you know the existence.
//
//                item2 = GameEngine.acquire(tgtState.characters[who]!!.resources.keys.toList())
//                amount2 = GameEngine.acquire(arrayListOf("1","2","3","4","5","6","7","8","9","10")).toInt()
//            }
//            "action"-> {
//                //println("When do you want the action to be executed?") TODO: this is not implemented yet.
//                //val meetingTime = tgtState.time+GameEngine.acquire(arrayListOf("3","6","9","12","18","21","24")).toInt()
//                println("Where do you want the action to be executed?")
//                val where = GameEngine.acquire(tgtState.places.map { it.value.name })
//                println("What action do you want to execute?")
//                val name = GameEngine.acquire(GameEngine.availableActions(tgtState, where, who))
//                action2 = Command(where, name, 0)
//            }
//            "information"->{
//                info2 = tgtState.informations[GameEngine.acquire(tgtState.informations.filter { it.value.knownTo.contains(who) and it.value.doesKnowExistence(tgtCharacter) and !it.value.knownTo.contains(tgtCharacter) }.map { it.key })] //You can only request information that you know the existence, but not the content.
//            }
//        }
        val tradeParams = GameEngine.acquire<TradeParams>("Trade", hashMapOf(
            "items1" to tgtState.characters[tgtCharacter]!!.resources,
            "items2" to tgtState.characters[who]!!.resources,
            "info1" to tgtState.informations.filter { it.value.knownTo.contains(tgtCharacter) and !it.value.doesKnowExistence(tgtCharacter) }.map { it.key }.toHashSet(),
            "info2" to tgtState.informations.filter { it.value.knownTo.contains(who) and it.value.doesKnowExistence(tgtCharacter) and !it.value.knownTo.contains(tgtCharacter) }.map { it.key }.toHashSet()))
        item = tradeParams.items1
        item2 = tradeParams.items2
        info = tradeParams.info1.firstOrNull()?.let { tgtState.informations[it] }
        info2 = tradeParams.info2.firstOrNull()?.let { tgtState.informations[it] }
        //TODO: = tradeParams.action1?.let { Command(it, 0) }
    }
    override fun execute() {
        var success= false
        val value = item.keys.sumOf {tgtState.characters[who]!!.itemValue(it)* item[it]!!}+(action?.let { tgtState.characters[who]!!.actionValue(it) } ?:.0)+ (info?.let { tgtState.characters[who]!!.infoValue(it) }?:.0)//Value is calculated based on how the opponent values the item, not how the tgtCharacter values it.
        val value2 = item2.keys.sumOf {tgtState.characters[who]!!.itemValue(it)* item2[it]!!}+(action2?.let {tgtState.characters[who]!!.actionValue(it)} ?:.0)+ (info2?.let { tgtState.characters[who]!!.infoValue(it) } ?:.0)
        val valuea = item.keys.sumOf {tgtState.characters[tgtCharacter]!!.itemValue(it)* item[it]!!}+(action?.let { tgtState.characters[tgtCharacter]!!.actionValue(it) } ?:.0)+ (info?.let { tgtState.characters[tgtCharacter]!!.infoValue(it) }?:.0)
        val valuea2 = item2.keys.sumOf {tgtState.characters[tgtCharacter]!!.itemValue(it)* item2[it]!! }+(action2?.let {tgtState.characters[tgtCharacter]!!.actionValue(it)} ?:.0)+ (info2?.let { tgtState.characters[tgtCharacter]!!.infoValue(it) } ?:.0)
        success = if(tgtState.nonPlayerAgents.keys.contains(who)) {
            tgtState.nonPlayerAgents[who]!!.decideTrade(tgtCharacter, value, value2, valuea, valuea2)
        } else//If player, acquires the decision from the player.
        {
            println("$tgtCharacter offers $item for $item2,\n and $action for $action2,\n and $info for $info2.")//TODO: use the trade interface.
            println("Do $who accept the trade?")
            GameEngine.acquire(listOf("yes", "no"))=="yes"
        }

        if(success) {
            if(item.isNotEmpty()){
                if(item.any{ (tgtState.characters[tgtCharacter]!!.resources[it.key] ?: 0) < item[it.key]!! }){
                    println("You don't have enough $item to trade.")
                    onFinished(false)
                    return
                }
                item.forEach{
                    tgtState.characters[tgtCharacter]!!.resources[it.key] = (tgtState.characters[tgtCharacter]!!.resources[it.key] ?: 0) - it.value
                    tgtState.characters[who]!!.resources[it.key] = (tgtState.characters[who]!!.resources[it.key] ?: 0) + it.value
                }
            }
            if (item2.isNotEmpty()) {
                if(item2.any{ (tgtState.characters[who]!!.resources[it.key] ?: 0) < item2[it.key]!! }){
                    println("They don't have enough $item2 to trade.")
                    onFinished(false)
                    return
                }
                item2.forEach{
                    tgtState.characters[who]!!.resources[it.key] = (tgtState.characters[who]!!.resources[it.key] ?: 0) - it.value
                    tgtState.characters[tgtCharacter]!!.resources[it.key] = (tgtState.characters[tgtCharacter]!!.resources[it.key] ?: 0) + it.value
                }
            }
            action?.let{ tgtState.nonPlayerAgents[tgtCharacter]!!.commands.add(it)}
            action2?.let{ tgtState.nonPlayerAgents[who]!!.commands.add(it)}
            info?.let{ tgtState.informations[it.name]!!.knownTo.add(who)}
            info2?.let{ tgtState.informations[it.name]!!.knownTo.add(tgtCharacter)}
            //Increase mutualities of each character with the other. Value is proportional to the value of the traded item.
            tgtState.setMutuality(who, tgtCharacter, value+value2)
            tgtState.setMutuality(tgtCharacter, who, valuea+valuea2)

            println("$who trades with $tgtCharacter.")
            onFinished(true)
        }
        else{
            println("$who refuses to trade with $tgtCharacter.")
            onFinished(false)
            //TODO: this should come with consequences.
        }
        tgtState.characters[tgtCharacter]!!.frozen++

    }


}