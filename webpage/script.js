var fromId = "",
    secret = "",
    socket = io.connect('http://192.168.0.104:8080');

socket.on("serverData", function (data) {
    console.log(data);
});

socket.on("inMessage", function (data) {
    console.log(data);
    var decryptedInfo = sjcl.decrypt(secret, data[2]);
    if (data[0] == fromId) {
        console.log(decryptedInfo);
    }
});

function idUpdate() {
    fromId = $("#fromId").val();
}

function secretUpdate() {
    secret = $("#secret").val();
}

function changePing() {
    toId = $("#toId").val();
    message = $("#message").val();
    if (toId != "" && fromId != "" && secret != "" && message != "") {
        var toSend = [toId, fromId, sjcl.encrypt(secret, message)];
        console.log("SENDING " + toSend);
        socket.emit("message", toSend);
        toId = "";
    }
}