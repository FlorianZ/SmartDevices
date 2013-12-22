/*
    Author: florianz

    Dropcam device handler based on the default Dropcam device code. Adds switch
    capabilities, which allows for turning the camera on and off.

    Capabilities:
        - Image Capture
        - Polling
        - Refresh
        - Switch
*/

metadata {
    simulator {
        status "image": "raw:C45F5708D89A4F3CB1A7EEEE2E0C73D900, image:C45F5708D89A4F3CB1A7EEEE2E0C73D9, result:00"

        reply "take C45F5708D89A4F3CB1A7EEEE2E0C73D9": "raw:C45F5708D89A4F3CB1A7EEEE2E0C73D900, image:C45F5708D89A4F3CB1A7EEEE2E0C73D9, result:00"
    }

    preferences {
        input "username", "text", title: "Username", description: "Your Drop Cam Username", required: true
        input "password", "password", title: "Password", description: "Your Drop Cam Password", required: true
        input "uuid", "text", title: "Device ID", description: "Your Drop Cam ID", required: true
    }

    tiles {
        standardTile("camera", "device.image", width: 1, height: 1, canChangeIcon: false, inactiveLabel: true, canChangeBackground: false) {
            state "default", label: "", action: "", icon: "st.camera.dropcam-centered", backgroundColor: "#FFFFFF"
        }

        carouselTile("cameraDetails", "device.image", width: 3, height: 2) { }

        standardTile("take", "device.image", width: 1, height: 1, canChangeIcon: false, inactiveLabel: true, canChangeBackground: false) {
            state "take", label: "Take", action: "Image Capture.take", icon: "st.camera.dropcam", backgroundColor: "#FFFFFF", nextState:"taking"
            state "taking", label:'Taking', action: "", icon: "st.camera.dropcam", backgroundColor: "#53a7c0"
            state "image", label: "Take", action: "Image Capture.take", icon: "st.camera.dropcam", backgroundColor: "#FFFFFF", nextState:"taking"
        }
        
        standardTile("switch", "device.switch", canChangeIcon: true) {
            state "on", label: 'On', action: "switch.off", icon: "st.switches.switch.on", backgroundColor: "#79b821"
            state "off", label: 'Off', action: "switch.on", icon: "st.switches.switch.off", backgroundColor: "#ffffff"
        }

        standardTile("refresh", "device.switch", inactiveLabel: false, decoration: "flat") {
            state "default", label:'', action:"refresh.refresh", icon:"st.secondary.refresh"
        }

        main "camera"
        details(["cameraDetails", "take", "switch", "refresh"])
    }
}

def updated() {
    log.debug "Updating Dropcam"
    refresh()
}

def parse(String description) {
    log.debug "Parsing: $description"
}

def take() {
    takePicture()
}

def on() {
    log.debug "Turning Dropcam on"
    setProperties("streaming.enabled", true)
    sendEvent(name: "switch", value: "on")
}

def off() {
    log.debug "Turning Dropcam off"
    setProperties("streaming.enabled", false)
    sendEvent(name: "switch", value: "off")
}

def poll() {
    refresh()
}

def refresh() {
    log.debug "Refreshing Dropcam"
    
    def items = getProperties()
    log.debug items
    
    def streamingEnabled = items?.'streaming.enabled'
    if (streamingEnabled != null) {
        if (streamingEnabled) {
            sendEvent(name: "switch", value: "on")
        } else {
            sendEvent(name: "switch", value: "off")
        }
        log.debug "Switch is now $streamingEnabled"
    }
}

private getPictureName() {
    def pictureUuid = java.util.UUID.randomUUID().toString().replaceAll('-', '')
    getCameraUuid() + "_$pictureUuid" + ".jpg"
}

private getCookieValue() {
    state.cookie
}

private getCameraUuid() {
    settings.uuid
}

private getImageWidth() {
    settings.image_width ?: 1280
}

private getUsername() {
    settings.username
}

private getPassword() {
    settings.password
}

private updateCookie(String cookie) {
    state.cookie = cookie
    device.updateDataValue("cookie", cookie)
}

private validUserAgent() {
    "curl/7.24.0 (x86_64-apple-darwin12.0) libcurl/7.24.0 OpenSSL/0.9.8x zlib/1.2.5"
}

private takePicture() {
    loginIfNeeded()
    def imageBytes
    httpGet(
        [
            uri: "https://nexusapi.dropcam.com",
            path: "/get_image",
            query: [width: getImageWidth(), uuid: getCameraUuid()],
            headers: [Cookie: getCookieValue(), 'User-Agent': validUserAgent()],
            requestContentType: "application/x-www-form-urlencoded",
        ],
        { response -> imageBytes = response.data }
    )
    if (imageBytes) {
        storeImage(getPictureName(), imageBytes)
        return true
    }
    return false
}

private getProperties() {
    loginIfNeeded()
    def content
    httpGet(
        [
            uri: "https://www.dropcam.com",
            path: "/api/v1/dropcams.get_properties",
            query: [uuid: getCameraUuid()],
            headers: [Cookie: getCookieValue(), 'User-Agent': validUserAgent()],
            requestContentType: "application/x-www-form-urlencoded"
        ],
        { response -> content = response.data }
    )
    def props = content?.items
    if (props) {
        return props[0]
    }
    return [:]
}

private setProperties(key, value) {
    loginIfNeeded()
    def success = false
    def uuid = getCameraUuid()
    httpPost(
        [
            uri: "https://www.dropcam.com",
            path: "/api/v1/dropcams.set_property",
            headers: [Cookie: getCookieValue(), 'User-Agent': validUserAgent()],
            requestContentType: "application/x-www-form-urlencoded",
            body: "uuid=$uuid&key=$key&value=$value"
        ],
        { response -> success = (response.status == 200) }
    )
    return success
}

private login() {
    def cookie
    httpPost(
        [
            uri: "https://www.dropcam.com",
            path: "/api/v1/login.login",
            body: [username: getUsername(), password: getPassword()],
            headers: ['User-Agent': validUserAgent()],
            requestContentType: "application/x-www-form-urlencoded"
        ],
        { response -> cookie = response.headers.'Set-Cookie'?.split(";")[0] }
    )
    if (cookie) {
        updateCookie(cookie)
        return true
    }
    return false
}

private getUserStatus() {
    def content
    httpGet(
        [
            uri: "https://www.dropcam.com",
            path: "/api/v1/users.get_current"
        ],
        { response -> content = response.data }
    )
    if (content) {
        return (content.status == 0)
    }
    return false
}

private loginIfNeeded() {
    if (!getUserStatus()) {
        return login()
    }
    return false
}
