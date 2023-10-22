package com.capsulezero.game.core.gameActions

import com.capsulezero.game.core.GameAction
import com.capsulezero.game.core.GameEngine
import com.capsulezero.game.core.GameState

class praise(targetState: GameState, targetCharacter: String, targetPlace: String) : GameAction(targetState, targetCharacter,
    targetPlace
) {
    var who = ""
    override fun chooseParams() {
        who =
            GameEngine.acquire(tgtState.ongoingMeetings.filter {it.value.currentCharacters.contains(tgtCharacter)}.flatMap { it.value.currentCharacters }+tgtState.ongoingConferences.filter {it.value.currentCharacters.contains(tgtCharacter)}.flatMap { it.value.currentCharacters })
    }
    override fun execute() {
        //TODO: praise
        if (tgtCharacter == who){ println("You praise yourself.");return}
        tgtState.setMutuality(tgtCharacter, who, 2.0)
        tgtState.characters[tgtCharacter]!!.frozen++
    }

}