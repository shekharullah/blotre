define(['./models'],
function(models)
{
"use-strict";

/**
*/
var StreamManager = function() {
    var self = this;
    self.streams = { };
    self.collections = { };

    var processStatusUpdate = function(msg) {
        var path = msg.stream.uri;
        (self.streams[path] || []).listeners.forEach(function(x) {
            x(msg.stream);
        });
    };

    var processCollectionStatusUpdate = function(msg) {
        var path = msg.from;
        (self.collections[path] || []).listeners.forEach(function(x) {
            x(msg.from, msg.stream);
        });
    };

    var processMessage = function(msg) {
        switch (msg.type) {
        case "StatusUpdate":
            processStatusUpdate(msg);

        case "CollectionStatusUpdate":
            processCollectionStatusUpdate(msg);
        }
    };

    self.socket = new WebSocket("ws://localhost:9000/v0/ws");
    self.ready = false;
    self.socket.onopen = function(e) {
        var targetStreams = Object.keys(self.streams);
        if (targetStreams.length) {
            self.socket.send(JSON.stringify({
                "type": "Subscribe",
                "to": targetStreams
            }));
        }

        var targetCollections = Object.keys(self.streams);
        if (targetCollections.length) {
            targetCollections.forEach(function(x) {
                self.socket.send(JSON.stringify({
                    "type": "SubscribeCollection",
                    "to": x
                }));
            });
        }
    };

    self.socket.onmessage = function(event) {
        console.log(event);
        var data = JSON.parse(event.data);
        if (data)
            processMessage(data);
    }
};

StreamManager.prototype.subscribe = function(path, callback) {
    this.subscribeAll([path], callback);
};

StreamManager.prototype.subscribeAll = function(paths, callback) {
    var self = this;

    var newSubscriptions = [];
    paths.forEach(function(path) {
        var current = self.streams[path];
        if (current) {
            current.listeners.push(callback);
        } else {
            self.streams[path] = { listeners: [callback] };
            newSubscriptions.push(path);
        }
    });

    if (newSubscriptions.length) {
        if (self.ready) {
            self.socket.send(JSON.stringify({
                "type": "Subscribe",
                "to": newSubscriptions
            }));
        }
    }
};

StreamManager.prototype.subscribeCollection = function(path, callback) {
    var self = this;

    var current = self.collections[path];
    if (current) {
        current.listeners.push(callback);
    } else {
        self.collections[path] = { listeners: [callback] };
        if (self.ready) {
            self.socket.send(JSON.stringify({
                "type": "SubscribeCollection",
                "to": path
            }));
        }
    }
};



return {
    StreamManager: StreamManager
};

});