process.stdin.resume();
process.stdin.setEncoding('utf8');

var app = require('http').createServer(handler)
  , io = require('socket.io').listen(app)
  , fs = require('fs')
  , url = require("url")

app.listen(8080);

function handler (req, res) {
    var url = req.url;
    var file = "../webpage/" + url;
    var answer = "fail";
    var fileType = url.split('.').pop();
    contentType = "";
    var requestedFile = "";
    switch (fileType) {
        case "png": contentType = "image/png"; break;
        case "jpg": contentType = "image/jpeg"; break;
        case "jpeg": contentType = "image/jpeg"; break;
        case "mp3": contentType = "audio/mpeg"; break;
        case "html": contentType = "text/html"; break;
        case "zip": contentType = "application/zip"; break;
        case "php": contentType = "application/php"; break;
        case "css": contentType = "text/css"; break;
        case "js": contentType = "application/x-javascript"; break;
        default: contentType = "text/plain"; break;
    }
    fs.readFile(file, function(err, data) {
        if (err) requestedFile = "something went wrong...";
        else requestedFile = data;
        res.writeHead(200, {"Content-Type": contentType});
        res.end(requestedFile);
    });
}

io.sockets.on("connection", function (socket) {
    socket.emit("serverData", "CONNECTED");
    socket.on("message", function (data) {
        console.log("MESSAGE TO " + data[0]);
        socket.broadcast.emit("inMessage", data);
    });
});

/*
io.sockets.on('connection', function (socket) {
    setInterval(function(){
        if (send != "") {
            console.log("PRESS REGISTERED");
            socket.emit('katt', send);
            send = "";
        }
    },10);
    socket.emit('news', { hello: 'world' });
    socket.on('my other event', function (data) {
        console.log(data);
    });
});

*/