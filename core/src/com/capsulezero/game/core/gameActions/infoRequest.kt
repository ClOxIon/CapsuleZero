package com.capsulezero.game.core.gameActions

import com.capsulezero.game.core.GameAction
import com.capsulezero.game.core.GameEngine
import com.capsulezero.game.core.GameState

class infoRequest(targetState: GameState, targetCharacter: String, targetPlace: String) : GameAction(targetState, targetCharacter,
    targetPlace
) {
    var who = hashSetOf<String>()
    var what = ""
    override fun chooseParams() {
        //TODO: ability to fabricate information
        what =
            GameEngine.acquire(tgtState.informations.filter { it.value.knowExistence.contains(tgtCharacter) }.map { it.key })

    }
    override fun execute() {
        who =
            (tgtState.ongoingMeetings.filter {it.value.currentCharacters.contains(tgtCharacter)}.flatMap { it.value.currentCharacters }+tgtState.ongoingConferences.filter {it.value.currentCharacters.contains(tgtCharacter)}.flatMap { it.value.currentCharacters }).toHashSet()

        if(!tgtState.informations.filter { it.value.knowExistence.contains(tgtCharacter) }.map { it.key }.contains(what))
            println("Warning: $tgtCharacter requested information $what that they don't know the existence.")
        else {
            //If someone knows about the information, then everyone in the meeting/conference knows about it.
            if (tgtState.informations[what]!!.knownTo.intersect(who).isNotEmpty())
                tgtState.informations[what]!!.knownTo += who
            else
                println("$tgtCharacter requested information, but no one knows about $what.")
        }

        tgtState.characters[tgtCharacter]!!.frozen++
    }

}