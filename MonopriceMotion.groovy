/**
 *  Monoprice Motion Sensor
 *
 *  Capabilities: Motion Sensor, Temperature Measurement
 *
 *  Author: FlorianZ
 *  Date: 2014-02-19
 */
metadata {
    simulator {
        status "inactive": "command: 2001, payload: 00"
        status "active": "command: 2001, payload: FF"
        
        for (int i = 0; i <= 100; i += 20) {
            status "temperature ${i}F": new physicalgraph.zwave.Zwave().sensorMultilevelV2.sensorMultilevelReport(
                scaledSensorValue: i, precision: 1, sensorType: 1, scale: 1).incomingMessage()
        }
    }
    
    tiles {
        standardTile("motion", "device.motion", width: 2, height: 2) {
            state("active", label:'motion', icon:"st.motion.motion.active", backgroundColor:"#53a7c0")
            state("inactive", label:'no motion', icon:"st.motion.motion.inactive", backgroundColor:"#ffffff")
        }
        
        // XXX: Add a setting for the desired temperature unit (C or F).
        //      How to change the valueTile's background color scale based
        //      on the set unit?
        
        valueTile("temperature", "device.temperature", inactiveLabel: false) {
            state "temperature", label:'${currentValue}°', unit:"F",
            backgroundColors:[
                [value: 31, color: "#153591"],
                [value: 44, color: "#1e9cbb"],
                [value: 59, color: "#90d2a7"],
                [value: 74, color: "#44b621"],
                [value: 84, color: "#f1d801"],
                [value: 95, color: "#d04e00"],
                [value: 96, color: "#bc2323"]
            ]
        }

        main(["motion", "temperature"])
        details(["motion", "temperature"])
    }
}

def c2f(value) {
    // Given a value in degrees centigrade, return degrees Fahrenheit

    (value * 9/5 + 32) as Integer
}

def filterSensorValue(value) {
    // The temperature readig reported often quickly bounces between two values,
    // adding a lot of noise in the activity log. In order to filter out the
    // noise, a basic low pass filter is applied below. 

    def maxHistory = 2
    if (!state.history) {
        state.history = []
    }
    state.history << value.toInteger()
    state.history = state.history.drop(state.history.size() - maxHistory)
    return (state.history.sum() / state.history.size()) as Integer
}

def parse(String description) {
    def result = []
    def cmd = zwave.parse(description, [0x20: 1, 0x31: 2, 0x84: 1])
    if (cmd) {
        if (cmd.CMD == "8407") {
            result << new physicalgraph.device.HubAction(zwave.wakeUpV1.wakeUpNoMoreInformation().format())
        }
        result << createEvent(zwaveEvent(cmd))
    }

    log.debug "Parse returned ${result}"
    return result
}

def zwaveEvent(physicalgraph.zwave.commands.wakeupv1.WakeUpNotification cmd) {
    def map = [:]
    map.value = "";
    map.descriptionText = "${device.displayName} woke up"
    return map
}

def zwaveEvent(physicalgraph.zwave.commands.basicv1.BasicSet cmd) {
    def map = [:]
    map.name = "motion"
    map.value = cmd.value ? "active" : "inactive"
    map.handlerName = map.value
    map.descriptionText = cmd.value ? "${device.displayName} detected motion" : "${device.displayName} motion has stopped"
    return map
}

def zwaveEvent(physicalgraph.zwave.commands.sensormultilevelv2.SensorMultilevelReport cmd) {
    def map = [:]
    if (cmd.sensorType == 1) {
        map.name = "temperature"
        
        // If the sensor returns the temperature value in degrees centigrade,
        // convert to degrees Fahrenheit. Also, apply a basic low-pass filter
        // to the scaled sensor value input.
        def filteredSensorValue = filterSensorValue(cmd.scaledSensorValue)
        if (cmd.scale == 1) {
            map.value = filteredSensorValue as String
            map.unit = "F"
        } else {
            map.value = c2f(filteredSensorValue) as String
            map.unit = "F"
        }
        map.descriptionText = "${device.displayName} temperature is ${map.value} ${map.unit}"
    }
    return map
}

def zwaveEvent(physicalgraph.zwave.Command cmd) {
    // Catch-all handler. The sensor does return some alarm values, which
    // could be useful if handled correctly (tamper alarm, etc.)
    [descriptionText: "Unhandled: ${device.displayName}: ${cmd}", displayed: false]
}
