/*
 * =============================  Inovelli Notification LED Child (Driver) ===============================
 *
 *  Copyright 2020 Robert Morris
 * 
 *  Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except
 *  in compliance with the License. You may obtain a copy of the License at:
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software distributed under the License is distributed
 *  on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the License
 *  for the specific language governing permissions and limitations under the License.
 *
 * =======================================================================================
 *
 *  Last modified: 2020-01-19
 * 
 *  Changelog:
 * 
 *  v1.0 - Initial Release
 *  v1.1 - Added options for "on" command behavior; minor fixes
 *
 */ 

import groovy.transform.Field

@Field static Map dimmerLEDEffects = [0: "Off", 1:"Solid", 2:"Chase", 3:"Fast Blink", 4:"Slow Blink", 5:"Pulse"]
@Field static Map switchLEDEffects = [0: "Off", 1:"Solid", 2:"Fast Blink", 3:"Slow Blink", 4:"Pulse"]

metadata {
    definition (name: "Inovelli Notification LED", namespace: "RMoRobert", author: "Robert Morris", importUrl: "https://raw.githubusercontent.com/RMoRobert/Hubitat/master/drivers/Inovelli/Inovelli-Notification-LED.groovy") {
        capability "Actuator"
        capability "ColorControl"
        capability "Switch"
        capability "SwitchLevel"
        capability "Light"
        capability "LightEffects"
        capability "Configuration"

        command "setDuration", ["NUMBER"]
        attribute "duration", "number"

        attribute "colorName", "string"
        attribute "effect", "string"
    }
       
   preferences {
        input(name: "hueModel", type: "enum", description: "", title: "Hue (color) model",
              options: [["default":"Hubitat default (0-100)"],["degrees":"Degrees (0-360)"],["inovelli":"Inovelli (0-255)"]], defaultValue: "default")
        input(name: "onAction", type: "enum", description: "", title: "Action for \"on\" command",
              options: [[0:"None"],[1:"Start effect: Solid"], [2:"Start effect: Chase (Dimmer)/Fast Blink (Switch)"],
                        [3:"Start effect: Fast Blink (Dimmer)/Slow Blink (Switch)"],[4: "Start effect: Slow Blink (Dimmer)/Pulse (Switch)"],
                        [5: "Start effect: Pluse (Dimmer only)"]], defaltValue: 0)
        input(name: "colorStaging", type: "bool", title: "Enable color pre-staging (required; use setEffect or on to start effect)", defaultValue: true)
        input(name: "levelStaging", type: "bool", title: "Enable level pre-staging (required; use setEffect or on to start effect)", defaultValue: true)
        input(name: "enableDebug", type: "bool", title: "Enable debug logging", defaultValue: true)
        input(name: "enableDesc", type: "bool", title: "Enable descriptionText logging", defaultValue: true)
    }
}

def installed(){
    log.debug "Installed..."

    doSendEvent("saturation", 100) // default value, will never change
    doSendEvent("hue", 0) // default value
    doSendEvent("level", 100) // default value
    doSendEvent("switch", "off") // default value

    initialize()
}

def updated(){
    log.debug "Updated..."
    initialize()
}

def initialize() {
    log.debug "Initializing"
    int disableTime = 1800
    if (enableDebug) {
        log.debug "Debug logging will be automatically disabled in ${disableTime} seconds"
        runIn(disableTime, debugOff)
    }
    configure()
}

def configure() {
    log.warn device.deviceNetworkId.find("31NotifyLED")
    log.warn "${device.deviceNetworkId.find('-31NotifyLED') ? 'LZW31-SN' : 'LZW30-SN'}"
    state.parentDeviceType = device.deviceNetworkId.find("-31NotifyLED") ? "LZW31-SN" : "LZW30-SN"
    def le = new groovy.json.JsonBuilder(state.parentDeviceType == "LZW31-SN" ? dimmerLEDEffects : switchLEDEffects)
    state.maxEffectNumber = (state.parentDeviceType == "LZW31-SN") ? 5 : 4
    sendEvent(name: "lightEffects", value: le)
}

def debugOff() {
    log.warn("Disabling debug logging")
    device.updateSetting("enableDebug", [value:"false", type:"bool"])
}

def on() {    
    logDebug("Setting child notification LED on (recommend to NOT do this and call setEffect with ID/name instead; on() is equivalent to setNextEffect())...")
    if (settings.onAction) {
        logDebug("\"On\" action configured to start effect ${settings.onAction}; starting...")
        setEffect(settings.onAction as int)
    } else {
        logDebug('No "on" action configured; ignoring on() command')
    }
}

def off() {
    logDebug("Setting child notification LED off...")
    parent.setIndicator(0)
    doSendEvent("switch", "off")
}

def setLevel(value) {
    logDebug("Setting child notification LED level to $value...")
    if (value < 1 || value > 100) value = 1
    doSendEvent("level", value, "%")
}

def setLevel(value, rate) {
    logDebug("Transition rate not supported; discarding value and setting level.")
    setLevel(value)
}

def setColor(value) {
    logDebug("Setting child LED color to $value (note: only hue and level values supported; saturation is ignored)...")
    if (value.hue == null ) {
        logDebug("Exiting setColor because no hue set")
        return
    }
    def h = value.hue
    def l = value.level
    if (h > 360) h = 100 // TODO: better range checking?
    doSendEvent("hue", h, null)
    setGenericName(h)
    doSendEvent("level", l, "%")
}

def setHue(value) {
    logDebug("Setting child LED hue to $value...")
    def h = value
    if (h > 360) h = 100 // TODO: better range checking?
    doSendEvent("hue", h, null)
    setGenericName(h)
}

def setSaturation(value) {
    logDebug("Child LED saturation control not supported; ignoring command")
}

def setDuration(duration) {
    logDebug("Setting default duration to $duration")
    doSendEvent("duration", duration, null)
}

def setEffect(String effect) {
    logDebug("Finding effect '$effect' to set...")
    def id 
    state.parentDeviceType == "LZW31-SN" ? 
        dimmerLEDEffects.find { it.value == effect } : switchLEDEffects.find { it.value == effect }
    if (id != null) setEffect(id.key)
}

def setEffect(id) {
    logDebug("Setting effect $id...")
    if (id < 0) id = 0
    if (id > state.maxEffectNumber) id = state.maxEffectNumber
    state.crntEffectId = id
    def effectName = (state.parentDeviceType == "LZW31-SN") ? dimmerLEDEffects[id] : switchLEDEffects[id]
    if (id != 0) {
        setNotificationLED()
    }
    else {
        off()
    }
    doSendEvent("effect", effectName ?: id)
    doSendEvent("switch", id ? "on" : "off")
}

def setNextEffect(){
    def currentEffect = state.crntEffectId ?: 0
    currentEffect++
    if (currentEffect > state.maxEffectNumber) currentEffect = 1
    setEffect(currentEffect)
}

def setPreviousEffect(){
    def currentEffect = state.crntEffectId ?: 2
    currentEffect--
    if (currentEffect < 1) currentEffect = state.maxEffectNumber
    setEffect(currentEffect)
}

def setNotificationLED() {
	def value = 0
    BigDecimal scaledHue = (device.currentValue("hue") != null) ? device.currentValue("hue").toBigDecimal() :  0
    if (hueModel == "default") scaledHue = Math.round(scaledHue * 2.55)
    else if (hueModel == "degrees") scaledHue = Math.round(scaledHue / 1.41)
    def color = device.currentValue("hue")
    def scaledLevel = Math.round(device.currentValue("level")/10) ?: 10
    def currDur = device.currentValue("duration")
    def duration = (currDur >= 1 && currDur <= 255) ? currDur : 255
	scaledHue = (scaledHue >= 0 && scaledHue <= 255) ? scaledHue : 255
	level = (level >= 0 && level <= 10) ? level : 10
	duration = (duration >= 1 && duration <= 255) ? duration : 255
	def type = state.crntEffectId ?: 4
    logDebug("Calculating notification paramter for color $scaledHue, level $scaledLevel, duration $duration, effect $type")
	value += (scaledHue as int) * 1
    value += (scaledLevel as int) * 256
    value += (duration as int) * 65536
    value += (type as int) * 16777216
    return parent.setIndicator(value)
}

def doSendEvent(eventName, eventValue, eventUnit=null) {
    logDebug("Creating event for $eventName...")
    def descriptionText = "${device.displayName} ${eventName} is ${eventValue}${eventUnit ?: ''}"
    logDesc(descriptionText)
    def event
    if (eventUnit) {
        event = sendEvent(name: eventName, value: eventValue, descriptionText: descriptionText, unit: eventUnit) 
    } else {
        event = sendEvent(name: eventName, value: eventValue, descriptionText: descriptionText) 
    }
    return event
}

// Hubiat-provided color/name mappings
def setGenericName(hue){
    def colorName
    hue = hue.toInteger()
    if (hueModel == "default") hue = (hue * 3.6)
    else if (hueModel == "inovelli") hue = (hue * 1.41)
    switch (hue.toInteger()){
        case 0..15: colorName = "Red"
            break
        case 16..45: colorName = "Orange"
            break
        case 46..75: colorName = "Yellow"
            break
        case 76..105: colorName = "Chartreuse"
            break
        case 106..135: colorName = "Green"
            break
        case 136..165: colorName = "Spring"
            break
        case 166..195: colorName = "Cyan"
            break
        case 196..225: colorName = "Azure"
            break
        case 226..255: colorName = "Blue"
            break
        case 256..285: colorName = "Violet"
            break
        case 286..315: colorName = "Magenta"
            break
        case 316..345: colorName = "Rose"
            break
        case 346..360: colorName = "Red"
            break
        default: colorName = "undefined"
            break
    }
    if (device.currentValue("colorName") != colorName) doSendEvent("colorName", colorName, null)
}

def logDebug(str) {
    if (settings.enableDebug) log.debug(str)
}

def logDesc(str) {
    if (settings.enableDesc) log.info(str)
}
