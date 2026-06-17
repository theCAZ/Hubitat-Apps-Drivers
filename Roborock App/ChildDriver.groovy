/**
 *  Roborock Vacuum (Local TCP) - Child Driver
 *
 *  Copyright 2025
 *
 *  Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except
 *  in compliance with the License. You may obtain a copy of the License at:
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 *  This is a thin child driver. All TCP communication and polling is handled
 *  by the parent app (Roborock Local TCP). This driver simply:
 *    - Exposes commands to the user on the device page
 *    - Calls parent.executeCommand() when a command is pressed
 *    - Receives status events pushed by the parent app via sendEvent()
 *
 *  DO NOT install this driver directly — it is created automatically
 *  by the Roborock Local TCP parent app.
 */
public static String version() { return "1.0.0" }

import groovy.transform.Field

@Field static final Map mopWaterModeCodes = [
    0:"Default",200:"Off",201:"Low",202:"Medium",203:"High",204:"Auto",207:"Custom"
]

metadata {
    definition(
        name:      "Roborock Vacuum (Local TCP)",
        namespace: "bloodtick-local",
        author:    "Hubitat",
        importUrl: ""
    ) {
        capability "Actuator"
        capability "Battery"
        capability "Initialize"
        capability "Refresh"
        capability "Switch"
        capability "PushableButton"

        // ── Primary controls ──────────────────────────────────────────
        command "vacuumStart"
        command "vacuumDock"
        command "vacuumPause"
        command "vacuumResume"

        // ── Room / Scene ──────────────────────────────────────────────
        command "vacuumRooms", [
            [name: "Room IDs*", type: "STRING",
             description: "Comma or space delimited room IDs from roomsList attribute"],
            [name: "MopWater",  type: "ENUM",
             description: "Mop water level (optional)",
             constraints: mopWaterModeCodes.values().collect { it.toUpperCase() }]
        ]
        command "vacuumScene", [[name: "Scene ID*", type: "STRING",
                                  description: "Scene ID from scenesList attribute"]]

        // ── Sync ──────────────────────────────────────────────────────
        command "syncFromCloud"

        // ── Attributes ────────────────────────────────────────────────
        attribute "dustCollection",                   "enum",   ["off","on"]
        attribute "dockError",                        "string"
        attribute "name",                             "string"
        attribute "state",                            "string"
        attribute "error",                            "string"
        attribute "fanPower",                         "string"
        attribute "cleanTime (min)",                  "number"
        attribute "cleanArea (sq ft)",                "number"
        attribute "cleanPercent (%)",                 "number"
        attribute "remainingFilter (%)",              "number"
        attribute "remainingMainBrush (%)",           "number"
        attribute "remainingSensors (%)",             "number"
        attribute "remainingSideBrush (%)",           "number"
        attribute "remainingHighSpeedMaintBrush (%)", "number"
        attribute "locating",                         "enum",   ["true","false"]
        attribute "mopMode",                          "string"
        attribute "mopWaterMode",                     "string"
        attribute "healthStatus",                     "enum",   ["offline","online"]
        attribute "roomsList",                        "string"
        attribute "scenesList",                       "string"
        attribute "localIP",                          "string"
        attribute "connectionMode",                   "enum",   ["local","cloud","offline"]
    }
}

preferences {
    input "logEnable",   "bool", title: "Enable Info Logging",  defaultValue: true
    input "debugEnable", "bool", title: "Enable Debug Logging", defaultValue: false
}

// ── Lifecycle ─────────────────────────────────────────────────────────────────
def installed() {
    logInfo "installed"
    sendEvent(name: "numberOfButtons", value: 1)
    sendEvent(name: "healthStatus",    value: "offline")
    sendEvent(name: "connectionMode",  value: "offline")
}

def updated() {
    logInfo "updated"
}

def initialize() {
    logInfo "initialize — requesting parent refresh"
    parent?.pollAllVacuums()
}

def refresh() {
    logInfo "refresh"
    parent?.pollAllVacuums()
}

// ── Commands — all delegate to parent app ─────────────────────────────────────
def on()  { vacuumStart() }
def off() { vacuumDock() }

def vacuumStart() {
    logInfo "vacuumStart"
    parentExecute("app_start", [])
}

def vacuumDock() {
    logInfo "vacuumDock"
    parentExecute("app_charge", [])
}

def vacuumPause() {
    logInfo "vacuumPause"
    parentExecute("app_pause", [])
}

def vacuumResume() {
    logInfo "vacuumResume"
    parentExecute("resume_segment_clean", [])
}

def vacuumRooms(String rooms, String mopWater = "DEFAULT") {
    logInfo "vacuumRooms: rooms=${rooms} mopWater=${mopWater}"
    // Set mop water if specified
    if (mopWater && mopWater.toUpperCase() != "DEFAULT") {
        Integer mopWaterCode = mopWaterModeCodes.find {
            it.value.toUpperCase() == mopWater.toUpperCase()
        }?.key
        if (mopWaterCode != null) {
            parentExecute("set_water_box_custom_mode", [mopWaterCode])
        }
    }
    // Parse room IDs
    List roomIds = rooms.replaceAll(" +", ",").split(",").collect {
        it.trim().toInteger()
    }
    parentExecute("app_segment_clean", roomIds)
}

def vacuumScene(String sceneId) {
    logInfo "vacuumScene: ${sceneId}"
    String duid = device.getDataValue("duid")
    if (!duid) { logWarn "vacuumScene: no duid set"; return }
    parent?.executeScene(duid, sceneId)
}

def syncFromCloud() {
    logInfo "syncFromCloud"
    parent?.discoverDevices()
}

def push(buttonNumber) {
    sendEvent(name: "pushed", value: buttonNumber, isStateChange: true)
}

// ── Helper: call parent with this device's duid ───────────────────────────────
private void parentExecute(String command, List params) {
    String duid = device.getDataValue("duid")
    if (!duid) {
        logWarn "parentExecute: no duid — device not fully configured yet"
        return
    }
    logDebug "parentExecute: duid=${duid} command=${command} params=${params}"
    parent?.executeCommand(duid, command, params)
}

// ── Logging ───────────────────────────────────────────────────────────────────
def logInfo(msg)  { if (logEnable   != false) log.info  "${device.displayName} ${msg}" }
def logDebug(msg) { if (debugEnable == true)  log.debug "${device.displayName} ${msg}" }
def logWarn(msg)  { log.warn  "${device.displayName} ${msg}" }
def logError(msg) { log.error "${device.displayName} ${msg}" }

