package com.capsulezero.game.core

import com.badlogic.gdx.Gdx
import com.badlogic.gdx.Input
import java.lang.Thread.sleep
import kotlin.math.log
import kotlin.math.min
import kotlin.random.Random
import kotlin.system.exitProcess

class GameEngine(val gameState: GameState) {
    val random = Random(System.currentTimeMillis())

    fun startGame() {
        gameState.updateUI.forEach { it(gameState) }//Update UI

        while (true) {
            gameState.characters.values.forEach {
                if(it.alive) {
                    if (it.frozen > 0) {
                        it.frozen--
                        if (!it.trait.contains("robot"))
                            it.health--
                    }
                    while (it.frozen == 0) {
                        performAction(it)
                        //if the action took any amount of time, exit the loop.
                    }
                }
            }
            progression()
        }
    }

    fun performAction(char: Character) {
        var action: GameAction
        var actionName: String
        val actionList = availableActions(
            this.gameState, gameState.places.values.find { it.characters.contains(char.name) }!!.name,
            char.name
        )
        if (char.name == gameState.playerAgent) {
            do {
                actionName = acquire(
                    actionList
                )
                action = Class.forName("com.capsulezero.game.core.gameActions.$actionName")
                    .getDeclaredConstructor(GameState::class.java, String::class.java, String::class.java).newInstance(
                        gameState,
                        char.name,
                        gameState.places.values.find { it.characters.contains(char.name) }!!.name
                    ) as GameAction
                action.chooseParams()
            } while (!action.isValid())


        } else {
            action = gameState.nonPlayerAgents[char.name]?.chooseAction(
                gameState,
                char.name,
                gameState.places.values.find { it.characters.contains(char.name) }!!.name
            ) ?: throw Exception("Non player character ${char.name} does not have a nonPlayerAgent.")
            if (action.javaClass.simpleName !in actionList)
                println(
                    "Warning: Non player character ${char.name} is performing ${action.javaClass.simpleName} at ${
                        gameState.places.values.find {
                            it.characters.contains(
                                char.name
                            )
                        }!!.name
                    }, time=${gameState.time}, which is not in the action list. This may be a bug."
                )

        }
        action.execute()

    }

    fun progression() {
        gameState.time += 1
        populationRedistribute()
        workAppratuses()
        conditionCheck()
        checkMarketResources(gameState)
        ageInformation()
        spreadPublicInfo()

        //println("Time: ${gameState.time}")
        //println("My approval:${gameState.characters[gameState.playerAgent]!!.approval}")
        gameState.updateUI.forEach { it(gameState) }
    }

    // information does not affect the approval after some time.
    private fun ageInformation() {
        val removed = arrayListOf<String>()
        gameState.informations.forEach {
            it.value.life--
            if (it.value.life <= 0)
                removed.add(it.key)
        }
        removed.forEach { gameState.informations.remove(it) }
    }

    private fun spreadPublicInfo() {
        //spread information within each party, if known.
        gameState.informations.values.forEach { information ->
            information.publicity.forEach {if(it.value>0)information.publicity[it.key] = it.value+1
            if(it.value>100)information.publicity[it.key] = 100}


        }

        gameState.parties.forEach {party->
            //incompatible information fight each other
            val l = gameState.informations.filter { it.value.publicity[party.key] != 0 }.toList()
            if(l.count()>1) {
                for (i in 0 until l.count() - 1)
                    for(j in i until l.count()) {
                        val a = l[i].second
                        val b = l[j].second

                        if (a.compatibility(b) == 0.0)//If the two information are incompatible
                        {
                            val aStrength =
                                if (a.author == "") 5000 /*rumor has fixed strength*/ else
                                    a.credibility * (party.value.individualMutuality(a.author) + a.supporters.sumOf { party.value.individualMutuality(it) } / 2/*supporter penalty*/)
                            val bStrength =
                                if (b.author == "") 5000 /*rumor has fixed strength*/ else
                                    b.credibility * (party.value.individualMutuality(b.author) + b.supporters.sumOf { party.value.individualMutuality(it) } / 2/*supporter penalty*/)
                            if (aStrength > bStrength) {
                                //Fight within each party
                                a.publicity.keys.forEach {
                                    b.publicity[it] = (b.publicity[it] ?: 1) - 1
                                }
                                if (gameState.characters[b.author] != null)
                                    //Characters in this party has decreased mutuality toward b.author and b.supporters, amount proportional to the party size
                                    party.value.members.forEach { gameState.setMutuality(it, b.author, -1.0)
                                        b.supporters.forEach { supporter -> gameState.setMutuality(it, supporter, -1.0) }
                                    }


                            }

                        }
                    }


            }

            //TODO: similar information merge into one. Do we really need this feature?

            //TODO: bad news affect the approval. casualty, stolen resource, low water ration oxygen, high wealth, crimes
            gameState.informations.filter { it.value.type == "casualty" }.forEach {
                var factor = 1.0
                if (it.value.author == "") factor = 2.0//rumors affect the approval negatively.
                //Casualty is cannot be everywhere, it should be localized to a party.
    //            if(it.value.tgtPlace == "everywhere")
    //            {
    //                gameState.characters.keys.forEach{char->
    //                    if(!char.trait.contains("robot"))
    //                        party.value.members.forEach { member->gameState.characters[member]!!.mutuality[char.name] = gameState.characters[member]!!.mutuality[char.name]!! - it.value.amount * (it.value.publicity[party.key] ?:0) / 1000 * factor}
    //                }
    //            }else
                    //If the casualty is in a specific place, approval of the responsible party drops.
                gameState.setPartyMutuality(party.key, gameState.places[it.value.tgtPlace]!!.responsibleParty , -it.value.amount * (it.value.publicity[party.key] ?:0) / 1000 * factor)

                //TODO: If casualty is in our party, mutuality of the responsible party drops even more.
                //TODO: if our party is responsible, integrity drops.
            }
            gameState.informations.filter { it.value.type == "action" && it.value.action == "unofficialResourceTransfer" }
                .forEach {
                    var factor = 1
                    if (it.value.author == "") factor = 2//rumors affect the approval negatively.

                    //party loses mutuality toward the responsible party. TODO: consider affecting the individual mutuality toward the perpetrator.
                    gameState.setPartyMutuality(party.key, gameState.places[it.value.tgtPlace]!!.responsibleParty , -log(it.value.amount.toDouble(), 2.0
                    ) * (it.value.publicity[party.key] ?:0) / 100 * factor)
                }
            //The fact that resource is low itself does not affect the mutuality.--------------------------------------------------------------------
//            gameState.informations.filter { it.value.type == "resources" && it.value.tgtPlace== "everywhere" && it.value.tgtResource in listOf("water", "oxygen", "ration") }
//                .forEach {
//                    var factor = 1
//                    if (it.value.author == "") factor = 2//rumors affect the approval negatively.
//                    var consumption = when(it.value.tgtResource){
//                        "water"->4
//                        "ration"->2
//                        "oxygen"->1
//                        else -> 0
//                    }
//                    if(it.value.amount==0)//If the resource is empty, approval of everyone except the robots drops at the maximum rate.
//                        gameState.characters.values.forEach{char->
//                            if(!char.trait.contains("robot"))
//                                char.approval-= consumption  * factor * 1
//                        }
//                    else//If the resource is less than 12 hours worth left, approval of everyone except the robots drops at the rate INVERSELY proportional to the amount of resource left.
//                        gameState.characters.values.forEach{char->
//                        if(!char.trait.contains("robot"))
//                            char.approval-= min(consumption  * factor * gameState.pop / it.value.amount, consumption  * factor)
//                    }
//
//                }
            //-----------------------------------------------------------------------------------------------------------------------------------------
        }

    }

    fun populationRedistribute() {
        var idlePop = gameState.pop
        gameState.places.values.forEach {
            if (it.workHoursStart < gameState.hour && it.workHoursEnd > gameState.hour) {
                if (it.isAccidentScene) return@forEach //If there is an accident, no one works until it is resolved.
                val idealWorker = it.apparatuses.sumOf { apparatus -> apparatus.idealWorker }
                if (it.plannedWorker > it.resources["water"]!!)//out of budget. Shut down the facility until the water is back.
                {
                    it.apparatuses.forEach { apparatus -> apparatus.currentWorker = 0 }
                    return@forEach
                }
                if (idlePop > it.plannedWorker) {
                    it.apparatuses.forEach lambda@{ apparatus ->
                        if (idealWorker == 0) return@lambda
                        apparatus.currentWorker =
                            it.plannedWorker * apparatus.idealWorker / idealWorker//Distribute workers according to ideal worker
                        idlePop -= apparatus.currentWorker
                    }
                } else {
                    val tmpPop = idlePop
                    it.apparatuses.forEach lambda@{ apparatus ->
                        if (idealWorker == 0) return@lambda
                        apparatus.currentWorker =
                            tmpPop * apparatus.idealWorker / idealWorker//Distribute workers according to ideal worker
                        idlePop -= apparatus.currentWorker
                    }
                }

            } else
                it.apparatuses.forEach { apparatus -> apparatus.currentWorker = 0 }
        }
    }

    fun workAppratuses() {
        gameState.places.forEach { entry ->
            entry.value.apparatuses.forEach app@{ apparatus ->
                apparatus.durability -= 1//Apparatuses are damaged over time. TODO: get rid of unexpected behaviors, if any
                if (apparatus.durability<0)
                    apparatus.durability=0
                //Check if it is workable------------------------------------------------------------------------------
                if(entry.value.isAccidentScene) return@app //If there is an accident, no one works until it is resolved.
                apparatus.currentProduction.forEach {
                    if(entry.value.resources[it.key]!!+it.value>entry.value.maxResources[it.key]!!)
                        return@app //If the resource is full, no one works.
                    if(apparatus.name=="waterRecycler" && it.key=="water") {
                        if (gameState.recyclableWater < it.value)
                            return@app //If there is not enough recyclable water, no one works.
                    }

                }
                if(isShortOfResources(apparatus, place = entry.value)!="")
                    return@app //If there is not enough resources, no one works.
                //-----------------------------------------------------------------------------------------------------
                apparatus.currentProduction.forEach {
                    entry.value.resources[it.key] = entry.value.resources[it.key]!! + it.value
                    if(apparatus.name=="waterRecycler" && it.key=="water")
                        gameState.recyclableWater-=it.value//Water is recycled.
                }
                val waterConsumption = apparatus.currentWorker
                entry.value.resources["water"] = entry.value.resources["water"]!! - waterConsumption //Distribute water to workers.
                gameState.marketWater+=waterConsumption
                apparatus.currentConsumption.forEach {
                    entry.value.resources[it.key] = entry.value.resources[it.key]!! - it.value
                }
                apparatus.currentDistribution.forEach {
                    when(it.key){
                        "water"->gameState.marketWater+=it.value
                        "oxygen"->gameState.marketOxygen+=it.value
                        "ration"->gameState.marketRation+=it.value
                    }
                }

                if (apparatus.currentGraveDanger > random.nextDouble()) {
                    //Catastrophic accident occurred.
                    println("Catastrophic accident occurred at: ${entry.value.name}")
                    entry.value.isAccidentScene = true
                    generateCatastrophicAccidents(gameState, entry.value)

                } else if (apparatus.currentDanger > random.nextDouble()) {
                    //Accident occurred.
                    println("Accident occurred at: ${entry.value.name}")
                    entry.value.isAccidentScene = true
                    generateAccidents(gameState, entry.value)

                }
                if(apparatus.name in listOf("waterStorage", "oxygenStorage", "metalStorage", "componentStorage", "rationStorage"))
                {
                    apparatus.durability += 1//Storages are not damaged if they are worked.
                }



            }

        }
    }
    fun checkMarketResources(tgtState: GameState){
        gameState.recyclableWater = gameState.recyclableWater*999/1000 //1/1000 of the water is lost

        if(gameState.marketWater<gameState.pop)
            println("Less than 12 hours of water out in the market.")
        if(gameState.marketWater>gameState.pop/24) {
            gameState.marketWater -= gameState.pop / 24 //2L/day consumption.
            gameState.recyclableWater += gameState.pop / 24 //No water is lost.
        }
        //TODO: adjust consumption rate when resource is low?
        //does not affect approval when resource is low.

        else {
            val death = gameState.pop / 100 +1
            gameState.pop -= death //Deaths from dehydration.
            println("Casualties: $death, due to dehydration. Pop left: ${gameState.pop}")
            Information(
                "",
                tgtState.time,
                "casualty",
                tgtPlace = "everywhere",
                amount = death
            ).also { /*spread rumor*/
                val cpy = Information(it); tgtState.informations[cpy.generateName()] = cpy; //cpy.publicity = 5
            }
        }
        if(gameState.marketOxygen<gameState.pop/4)
            println("Less than 12 hours of oxygen out in the market.")
        if(gameState.marketOxygen>gameState.pop/96)
            gameState.marketOxygen-=gameState.pop/96 //0.5kg/day consumption.
        else {
            val death = gameState.pop / 100 +1//TODO: adjust deaths. Also, productivity starts to drop when oxygen is low.
            gameState.pop -= death //Deaths from suffocation.
            println("Casualties: $death, due to suffocation. Pop left: ${gameState.pop}")
            Information(
                "",
                tgtState.time,
                "casualty",
                tgtPlace = "everywhere",
                amount = death
            ).also { /*spread rumor*/
                val cpy = Information(it); tgtState.informations[cpy.generateName()] = cpy; //cpy.publicity = 5
            }
        }
        if(gameState.marketRation<gameState.pop/2)
            println("Less than 12 hours of ration out in the market.")
        if(gameState.marketRation>gameState.pop/48)
            gameState.marketRation-=gameState.pop/48 //1kg/day consumption.
        else {
            val death = gameState.pop / 100 +1//TODO: adjust deaths.
            gameState.pop -= death //Deaths from starvation.
            println("Casualties: $death, due to starvation. Pop left: ${gameState.pop}")
            Information(
                "",
                tgtState.time,
                "casualty",
                tgtPlace = "everywhere",
                amount = death
            ).also { /*spread rumor*/
                val cpy = Information(it); tgtState.informations[cpy.generateName()] = cpy; //cpy.publicity = 5
            }
        }

    }

    fun generateAccidents(tgtState: GameState, tgtPlace: Place) {
        //Generate casualties.
        val death = tgtPlace.currentWorker / 100 + 1 //TODO: what about injuries?
        gameState.pop -= death
        Information(
            "",
            tgtState.time,
            "casualty",
            tgtPlace = tgtPlace.name,
            amount = death
        )/*store dummy info*/.also { tgtPlace.accidentInformations[it.generateName()] = it }.also { /*spread rumor*/
            val cpy = Information(it); tgtState.informations[cpy.generateName()] = cpy; cpy.publicity[tgtPlace.responsibleParty] = 5
        }
            .also { /*copy this information to the responsible character.*/
                val cpy = Information(it); cpy.author =
                    gameState.parties[tgtPlace.responsibleParty]!!.leader; tgtState.informations[cpy.generateName()] =
                    cpy; cpy.publicity[tgtPlace.responsibleParty] = 0
            }

        //Generate resource loss.
        tgtPlace.resources["water"] = tgtPlace.resources["water"]!! - 50
        Information(
            "",
            tgtState.time,
            "lostResource",
            tgtPlace = tgtPlace.name,
            amount = death,
            tgtResource = "water"
        )/*store dummy info*/.also { tgtPlace.accidentInformations[it.generateName()] = it }.also { /*spread rumor*/
            val cpy = Information(it); tgtState.informations[cpy.generateName()] = cpy; cpy.publicity[tgtPlace.responsibleParty] = 5
        }
            .also { /*copy this information to the responsible character.*/
                val cpy = Information(it); cpy.author =
                    gameState.parties[tgtPlace.responsibleParty]!!.leader; tgtState.informations[cpy.generateName()] =
                    cpy; cpy.publicity[tgtPlace.responsibleParty] = 0
            }

        //Generate apparatus damage.
        tgtPlace.apparatuses.forEach{ app ->
            val tmp = tgtPlace.maxResources
            app.durability-=30
            if(app.durability<=0) {
                app.durability = 0
                //If storage durability = 0, lose resources.
                if (app.name in listOf("waterStorage", "oxygenStorage", "metalStorage", "componentStorage", "rationStorage")) {
                    val resourceName = app.name.substring(0, app.name.length - 7)
                    tgtPlace.resources[resourceName] = tgtPlace.resources[resourceName]!!*tgtPlace.maxResources[resourceName]!!/tmp[resourceName]!!
                    //For example, unbroken storage number 8->7 then lose 1/8 of the resource.
                }

                    Information(
                    "",
                    tgtState.time,
                    "damagedApparatus",
                    tgtPlace = tgtPlace.name,
                    amount = death,
                    tgtApparatus = app.name
                )/*store dummy info*/.also { tgtPlace.accidentInformations[it.generateName()] = it }.also { /*spread rumor*/
                    val cpy = Information(it); tgtState.informations[cpy.generateName()] = cpy; cpy.publicity[tgtPlace.responsibleParty] = 5
                }
                    .also { /*copy this information to the responsible character.*/
                        val cpy = Information(it); cpy.author =
                            gameState.parties[tgtPlace.responsibleParty]!!.leader; tgtState.informations[cpy.generateName()] =
                            cpy; cpy.publicity[tgtPlace.responsibleParty] = 0
                    }
            }
        }


    }

    fun generateCatastrophicAccidents(tgtState: GameState, tgtPlace: Place) {
        //Generate casualties.
        val death = tgtPlace.currentWorker / 5 + 1 //TODO: what about injuries?
        gameState.pop -= death
        Information(
            "",
            tgtState.time,
            "casualty",
            tgtPlace = tgtPlace.name,
            amount = death
        ).also { tgtPlace.accidentInformations[it.generateName()] = it }.also { /*spread rumor*/
            val cpy = Information(it); tgtState.informations[cpy.generateName()] = cpy; cpy.publicity[tgtPlace.responsibleParty] = 75
        }
            .also { information -> /*copy this information to the responsible character.*/
                val cpy = Information(information); cpy.author =
                    gameState.parties[tgtPlace.responsibleParty]!!.leader; tgtState.informations[cpy.generateName()] =
                    cpy; cpy.publicity[tgtPlace.responsibleParty] = 0
            }

        //Generate resource loss.
        tgtPlace.resources["water"] = tgtPlace.resources["water"]!! - 50
        Information(
            "",
            tgtState.time,
            "lostResource",
            tgtPlace = tgtPlace.name,
            amount = death,
            tgtResource = "water"
        ).also { tgtPlace.accidentInformations[it.generateName()] = it }.also { /*spread rumor*/
            val cpy = Information(it); tgtState.informations[cpy.generateName()] = cpy; cpy.publicity[tgtPlace.responsibleParty] = 75
        }
            .also { /*copy this information to the responsible character.*/
                val cpy = Information(it); cpy.author =
                    gameState.parties[tgtPlace.responsibleParty]!!.leader; tgtState.informations[cpy.generateName()] =
                    cpy; cpy.publicity[tgtPlace.responsibleParty] = 0
            }

        //Generate apparatus damage.
        tgtPlace.apparatuses.forEach{ app ->
            val tmp = tgtPlace.maxResources
            app.durability-=75
            if(app.durability<=0) {
                app.durability = 0
                //If storage durability = 0, lose resources.
                if (app.name in listOf("waterStorage", "oxygenStorage", "metalStorage", "componentStorage", "rationStorage")) {
                    val resourceName = app.name.substring(0, app.name.length - 7)
                    tgtPlace.resources[resourceName] = tgtPlace.resources[resourceName]!!*tgtPlace.maxResources[resourceName]!!/tmp[resourceName]!!
                    //For example, unbroken storage number 8->7 then lose 1/8 of the resource.
                }

                Information(
                    "",
                    tgtState.time,
                    "damagedApparatus",
                    tgtPlace = tgtPlace.name,
                    tgtApparatus = app.name
                )/*store dummy info*/.also { tgtPlace.accidentInformations[it.generateName()] = it }.also { /*spread rumor*/
                    val cpy = Information(it); tgtState.informations[cpy.generateName()] = cpy; cpy.publicity[tgtPlace.responsibleParty] = 75
                }
                    .also { /*copy this information to the responsible character.*/
                        val cpy = Information(it); cpy.author =
                            gameState.parties[tgtPlace.responsibleParty]!!.leader; tgtState.informations[cpy.generateName()] =
                            cpy; cpy.publicity[tgtPlace.responsibleParty] = 0
                    }
            }
        }
    }



    //TODO: Check for win/lose/interrupt conditions
    fun conditionCheck() {
        gameState.characters.forEach { entry ->
            if (entry.value.health <= 0) {
                println("${entry.value.name} died.")
                //TODO: Do we need to gameState.pop -= 1
                entry.value.alive = false
            }
        }
        val l = gameState.characters.filter {it.value.alive && !it.value.trait.contains("robot")}
        if(!l.contains(gameState.playerAgent)) {

            println("You died. Game over.")
            gameState.dump()
            exitProcess(0)

        }
        else if (l.size == 1)
        {
            println("You are the last survivor.")
            gameState.dump()
            exitProcess(0)
        }

        if (gameState.time % 720 == 0) { //Every 15 days, reset the budget.
            gameState.isBudgetProposed = false
            gameState.isBudgetResolved = false
        }
        if (gameState.time % 48 == 0) { //Every day, inform the infrastructure minister about total resource.
            val infraName = gameState.characters.values.find { it.division=="infrastructure" }!!.name
            Information(infraName, gameState.time, tgtTime = gameState.time, type = "resource", tgtResource = "water", tgtPlace = "everywhere", amount = gameState.places.values.sumOf { it.resources["water"]?:0 }).also { it.knownTo.add(infraName);gameState.informations[it.generateName()] = it }
            Information(infraName, gameState.time, tgtTime = gameState.time, type = "resource", tgtResource = "oxygen", tgtPlace = "everywhere", amount = gameState.places.values.sumOf { it.resources["oxygen"]?:0 }).also { it.knownTo.add(infraName);gameState.informations[it.generateName()] = it }
            Information(infraName, gameState.time, tgtTime = gameState.time, type = "resource", tgtResource = "ration", tgtPlace = "everywhere", amount = gameState.places.values.sumOf { it.resources["ration"]?:0 }).also { it.knownTo.add(infraName);gameState.informations[it.generateName()] = it }

        }

    }

    companion object {
        fun isShortOfResources(app: Apparatus, place: Place):String
        {
            app.currentConsumption.forEach {
                if((place.resources[it.key]?:0)<it.value)
                    return it.key //If the resource is empty, no one works.
            }
            //Distribution does not consume resource.
            /*app.currentDistribution.forEach {
                when(it.key){
                    "water"->if((place.resources[it.key]?:0)<it.value+ (app.currentConsumption["water"]?:0)) return "water"
                    "oxygen"->if((place.resources[it.key]?:0)<it.value+ (app.currentConsumption["oxygen"]?:0)) return "oxygen"
                    "ration"->if((place.resources[it.key]?:0)<it.value+ (app.currentConsumption["ration"]?:0)) return "ration"
                }
            }*/
            return ""

        }
        fun acquire(choices: List<String>): String {
            println(choices)
            while (true) {
                if (Gdx.input.isKeyPressed(Input.Keys.NUM_1)) {
                    sleep(500)
                    return choices[0]
                }
                if (Gdx.input.isKeyPressed(Input.Keys.NUM_2)) {
                    sleep(500)
                    return choices[min(1, choices.size - 1)]
                }
                if (Gdx.input.isKeyPressed(Input.Keys.NUM_3)) {
                    sleep(500)
                    return choices[min(2, choices.size - 1)]
                }
                if (Gdx.input.isKeyPressed(Input.Keys.NUM_4)) {
                    sleep(500)
                    return choices[min(3, choices.size - 1)]
                }
                if (Gdx.input.isKeyPressed(Input.Keys.NUM_5)) {
                    sleep(500)
                    return choices[min(4, choices.size - 1)]
                }
                if (Gdx.input.isKeyPressed(Input.Keys.NUM_6)) {
                    sleep(500)
                    return choices[min(5, choices.size - 1)]
                }
                if (Gdx.input.isKeyPressed(Input.Keys.NUM_7)) {
                    sleep(500)
                    return choices[min(6, choices.size - 1)]
                }
                if (Gdx.input.isKeyPressed(Input.Keys.NUM_8)) {
                    sleep(500)
                    return choices[min(7, choices.size - 1)]
                }
                if (Gdx.input.isKeyPressed(Input.Keys.NUM_9)) {
                    sleep(500)
                    return choices[min(8, choices.size - 1)]
                }
                sleep(50)
            }
        }

        fun availableActions(gameState: GameState, place: String, character: String): ArrayList<String> {
            val actions = arrayListOf<String>()
            if (gameState.ongoingMeetings.any { it.value.currentCharacters.contains(character) }) {
                if (character == gameState.playerAgent) {
                    println("You are in a meeting.")
                    println("Attendees: ${gameState.ongoingMeetings.filter { it.value.currentCharacters.contains(character) }.values.first().currentCharacters}")
                }
                actions.add("chat")
                actions.add("praise")
                actions.add("criticize")
                actions.add("unofficialCommand")
                actions.add("unofficialInfoShare")
                actions.add("unofficialInfoRequest")
                actions.add("appointMeeting")
                actions.add("wait")
                actions.add("leaveMeeting")
                return actions
            }
            if (gameState.ongoingConferences.any { it.value.currentCharacters.contains(character) }) {
                if (character == gameState.playerAgent) {
                    println("You are in a conference.")
                    println(
                        "Attendees: ${
                            gameState.ongoingConferences.filter {
                                it.value.currentCharacters.contains(
                                    character
                                )
                            }.values.first().currentCharacters
                        }"
                    )
                }
                val subject = gameState.ongoingConferences.firstNotNullOf { entry ->
                    entry.value.subject.takeIf {
                        entry.value.currentCharacters.contains(character)
                    }
                }
                when (subject) {
                    "budgetProposal" -> if (!gameState.isBudgetProposed) actions.add("budgetProposal")
                    "budgetResolution" -> if (!gameState.isBudgetResolved) actions.add("budgetResolution")
                }
                actions.add("praise")
                actions.add("criticize")
                actions.add("command")
                actions.add("infoShare")
                actions.add("infoRequest")
                actions.add("appointMeeting")
                actions.add("wait")
                actions.add("leaveConference")
                return actions
            }
            if (place != "home" && gameState.places[place]!!.characters.count() > 1)
                actions.add("talk")
            if (gameState.places[place]!!.isAccidentScene) {
                if(gameState.characters[character]!!.division== gameState.places[place]!!.responsibleParty)//Only the division leader can clear the accident scene.
                    actions.add("clearAccidentScene")
                actions.add("investigateAccidentScene")
            }
            actions.add("move")
            actions.add("examine")
            //actions.add("radio")
            actions.add("wait")
            if (place == "home") {
                actions.add("sleep")
                actions.add("eat")
            }
            if (place == gameState.characters[character]!!.home) {
                actions.add("home")
            }
            if (place == "mainControlRoom") {
                if (gameState.characters[character]!!.trait.contains("mechanic")) {
                    val availableConferences =
                        gameState.scheduledConferences.filter { it.value.time + 2 > gameState.time && gameState.time + 2 > it.value.time }
                            .filter { !gameState.ongoingMeetings.containsKey(it.key) }
                    if (availableConferences.isNotEmpty())
                        actions.add("startConference")
                } else {
                    val ongoingConferences = gameState.ongoingConferences.filter {
                        it.value.characters.contains(character) && !it.value.currentCharacters.contains(character)
                    }
                    if (ongoingConferences.isNotEmpty())
                        actions.add("joinConference")
                }
            }
            if (place == "mainControlRoom" || place == "market" || place == "squareNorth" || place == "squareSouth") {
                actions.add("infoAnnounce")
            }
            if ((gameState.places[place]!!.responsibleParty == gameState.characters[character]!!.division) && gameState.places[place]!!.responsibleParty != "") {
                actions.add("unofficialResourceTransfer")//can only steal from their own division.
                actions.add("officialResourceTransfer")//can only move resources from their own division.
            }
            val availableMeetings =
                gameState.scheduledMeetings.filter { it.value.time + 2 > gameState.time && gameState.time + 2 > it.value.time && it.value.place == place }
                    .filter { !gameState.ongoingMeetings.containsKey(it.key) }
                    .filter { it.value.characters.contains(character) }
            if (availableMeetings.isNotEmpty())
                actions.add("startMeeting")
            val ongoingMeetings = gameState.ongoingMeetings.filter {
                it.value.characters.contains(character) && !it.value.currentCharacters.contains(character) && it.value.place == place
            }
            if (ongoingMeetings.isNotEmpty()) {
                val subject = gameState.ongoingMeetings.firstNotNullOf { entry ->
                    entry.value.subject.takeIf {
                        entry.value.characters.contains(character) && !entry.value.currentCharacters.contains(character) && entry.value.place == place
                    }
                }
                if (gameState.characters[character]!!.trait.contains("mechanic")) {
                    when (subject) {
                        "talk" -> println("Someone wants to talk to you.")//TODO: have no idea what this is doing.
                    }
                }
                actions.add("joinMeeting")
            }
            if (gameState.characters[character]!!.trait.contains("mechanic") && place != "home") {actions.add("repair")}//TODO: Not only the mechanic can repair.
            return actions
        }
    }
}