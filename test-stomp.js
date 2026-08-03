const SockJS = require('sockjs-client');
const Stomp = require('stompjs');

// Generate a temporary user ID for testing
const userId = 'user-' + Math.floor(Math.random() * 10000);
console.log('Using temporary user ID:', userId);

// Create WebSocket connection
const socket = new SockJS('http://localhost:8080/game');
const client = Stomp.over(socket);

// Enable debug output to see what's happening
client.debug = function(str) {
    console.log('🔍 STOMP Debug:', str);
};

console.log('🔗 Connecting to STOMP...');

let currentGameId = null; // Track the game ID for subscriptions

client.connect({ 'login': userId }, function(frame) {
    console.log('✅ Connected to STOMP!');
    console.log('Session ID:', frame.headers['user-name'] || userId);

    // Subscribe to personal responses
    client.subscribe('/user/queue/response', function(message) {
        const response = JSON.parse(message.body);
        console.log('📨 Response:', response.type, '-', response.message);

        if (response.data) {
            console.log('📊 Game State:', {
                currentPlayer: response.data.currentPlayerName,
                dice: response.data.dice
            });
        }

        // AUTO-SUBSCRIBE: Extract and subscribe when game is created
        if (response.type === 'GAME_CREATED') {
            const match = response.message.match(/Game (\w+) created/);
            if (match) {
                currentGameId = match[1];
                console.log('🎮 Auto-subscribing to game:', currentGameId);
                subscribeToGameEvents(currentGameId);
            }
        }

        // AUTO-SUBSCRIBE: Subscribe when joining a game
        if (response.type === 'JOINED_GAME') {
            if (currentGameId) {
                console.log('🎮 Auto-subscribing to joined game:', currentGameId);
                subscribeToGameEvents(currentGameId);
            }
        }
    });

    console.log('\n📋 Available commands:');
    console.log('1. Type "create" to create game');
    console.log('2. Type "join GAME_ID" to join game');
    console.log('3. Type "roll" to roll dice');
    console.log('4. Type "choice N" to make choice');
    console.log('5. Type "state" to get game state');
    console.log('6. Type "exit" to quit\n');

    // Handle user input
    process.stdin.setEncoding('utf8');
    process.stdin.on('data', function(input) {
        const command = input.trim();

        if (command === 'create') {
            console.log('🎯 Creating game...');
            client.send('/app/game.create', {}, JSON.stringify({}));

        } else if (command.startsWith('join ')) {
            const gameId = command.split(' ')[1];
            currentGameId = gameId; // Store for auto-subscription
            console.log('🎯 Joining game:', gameId);
            client.send('/app/game.join', {}, JSON.stringify({gameId: gameId}));

        } else if (command === 'roll') {
            console.log('🎲 Rolling dice...');
            client.send('/app/game.roll', {}, JSON.stringify({}));

        } else if (command.startsWith('choice ')) {
            const choice = parseInt(command.split(' ')[1]);
            console.log('🎯 Making choice:', choice);
            client.send('/app/game.choice', {}, JSON.stringify({choice: choice}));

        } else if (command === 'state') {
            console.log('📊 Getting game state...');
            client.send('/app/game.state', {}, JSON.stringify({}));

        } else if (command === 'exit') {
            console.log('👋 Disconnecting...');
            client.disconnect();
            process.exit(0);

        } else {
            console.log('❌ Unknown command:', command);
        }
    });

}, function(error) {
    console.log('❌ STOMP Error:', error);
    process.exit(1);
});

// Function to subscribe to all game events for a specific game
function subscribeToGameEvents(gameId) {
    console.log('🔔 Subscribing to game events for:', gameId);

    // Subscribe to general game events
    client.subscribe('/topic/game/' + gameId + '/events', function(message) {
        const event = JSON.parse(message.body);
        console.log('🎮 Game Event:', event.type, '-', event.message);
        if (event.data) {
            console.log('📊 Event Data:', {
                currentPlayer: event.data.currentPlayerName,
                dice: event.data.dice,
                gameOver: event.data.gameOver
            });
        }
    });

    // Subscribe to dice roll events
    client.subscribe('/topic/game/' + gameId + '/dice', function(message) {
        const diceEvent = JSON.parse(message.body);
        console.log('🎲 Dice Event:', diceEvent.type, '-', diceEvent.message);
        if (diceEvent.data && diceEvent.data.dice) {
            console.log('🎯 Dice Result:', diceEvent.data.dice.die1, 'and', diceEvent.data.dice.die2);
        }
    });

    // Subscribe to move events
    client.subscribe('/topic/game/' + gameId + '/moves', function(message) {
        const moveEvent = JSON.parse(message.body);
        console.log('🚀 Move Event:', moveEvent.type, '-', moveEvent.message);
    });

    // Subscribe to turn events
    client.subscribe('/topic/game/' + gameId + '/turns', function(message) {
        const turnEvent = JSON.parse(message.body);
        console.log('🔄 Turn Event:', turnEvent.type, '-', turnEvent.message);
    });

    // Subscribe to game state updates
    client.subscribe('/topic/game/' + gameId + '/state', function(message) {
        const stateEvent = JSON.parse(message.body);
        console.log('📋 State Update:', stateEvent.type, '-', stateEvent.message);
    });

    console.log('✅ Subscribed to all game topics for game:', gameId);
}