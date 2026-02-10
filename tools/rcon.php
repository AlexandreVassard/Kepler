<?php
$RCON_HOST = '192.168.1.41';
$RCON_PORT = 12309;

$commands = [
    'hotel_alert'     => ['message', 'sender'],
    'refresh_looks'   => ['userId'],
    'refresh_club'    => ['userId'],
    'refresh_hand'    => ['userId'],
    'refresh_credits' => ['userId'],
    'refresh_motto'   => ['userId'],
    'user_alert'      => ['userId', 'message', 'sender'],
    'disconnect_user' => ['userId'],
    'room_alert'      => ['roomId', 'message', 'sender'],
    'refresh_catalogue' => [],
    'kick_user'         => ['userId'],
    'mute_user'         => ['userId', 'minutes'],
    'unmute_user'       => ['userId'],
];

$result = null;

if ($_SERVER['REQUEST_METHOD'] === 'POST') {
    $command = $_POST['command'] ?? '';
    $params = [];

    if (isset($commands[$command])) {
        foreach ($commands[$command] as $key) {
            $val = trim($_POST[$key] ?? '');
            if ($val !== '') {
                $params[$key] = $val;
            }
        }
    }

    $result = sendRcon($RCON_HOST, $RCON_PORT, $command, $params);
}

function sendRcon(string $host, int $port, string $header, array $params): string
{
    $message = '';
    $message .= pack('N', strlen($header));
    $message .= $header;
    $message .= pack('N', count($params));

    foreach ($params as $k => $v) {
        $message .= pack('N', strlen($k));
        $message .= $k;
        $message .= pack('N', strlen($v));
        $message .= $v;
    }

    $buffer = pack('N', strlen($message)) . $message;

    $socket = @socket_create(AF_INET, SOCK_STREAM, getprotobyname('tcp'));

    if (!$socket) {
        return 'Failed to create socket';
    }

    if (!@socket_connect($socket, $host, $port)) {
        $err = socket_strerror(socket_last_error($socket));
        @socket_close($socket);
        return "Connection failed: $err";
    }

    @socket_send($socket, $buffer, strlen($buffer), MSG_DONTROUTE);
    @socket_close($socket);

    return "Sent '$header' with " . count($params) . " param(s)";
}
?>
<!DOCTYPE html>
<html lang="en">
<head>
<meta charset="UTF-8">
<meta name="viewport" content="width=device-width, initial-scale=1">
<title>Kepler RCON</title>
<style>
  * { box-sizing: border-box; margin: 0; padding: 0; }
  body { font-family: system-ui, sans-serif; background: #1a1a2e; color: #eee; padding: 2rem; }
  h1 { margin-bottom: 1.5rem; font-size: 1.4rem; color: #e94560; }
  form { background: #16213e; padding: 1.5rem; border-radius: 8px; max-width: 480px; }
  label { display: block; margin-bottom: .3rem; font-size: .85rem; color: #aaa; }
  select, input, button { width: 100%; padding: .6rem; margin-bottom: 1rem; border: 1px solid #333; border-radius: 4px; background: #0f3460; color: #eee; font-size: .95rem; }
  select:focus, input:focus { outline: none; border-color: #e94560; }
  button { background: #e94560; border: none; cursor: pointer; font-weight: bold; }
  button:hover { background: #c73652; }
  .param { display: none; }
  .result { margin-top: 1rem; padding: .8rem; background: #0f3460; border-radius: 4px; font-family: monospace; max-width: 480px; }
  .result.ok { border-left: 3px solid #4ecca3; }
  .result.err { border-left: 3px solid #e94560; }
</style>
</head>
<body>
<h1>Kepler RCON</h1>

<?php if ($result): ?>
  <div class="result <?= str_starts_with($result, 'Sent') ? 'ok' : 'err' ?>"><?= htmlspecialchars($result) ?></div>
<?php endif; ?>

<form method="post">
  <label for="command">Command</label>
  <select name="command" id="command" onchange="toggleParams()">
    <?php foreach ($commands as $cmd => $keys): ?>
      <option value="<?= $cmd ?>" <?= (($_POST['command'] ?? '') === $cmd) ? 'selected' : '' ?>><?= $cmd ?></option>
    <?php endforeach; ?>
  </select>

  <?php foreach ($commands as $cmd => $keys): ?>
    <?php foreach ($keys as $key): ?>
      <div class="param" data-cmd="<?= $cmd ?>">
        <label for="<?= $cmd ?>_<?= $key ?>"><?= $key ?></label>
        <input type="text" name="<?= $key ?>" id="<?= $cmd ?>_<?= $key ?>"
               value="<?= htmlspecialchars($_POST[$key] ?? '') ?>"
               placeholder="<?= $key ?>">
      </div>
    <?php endforeach; ?>
  <?php endforeach; ?>

  <button type="submit">Send</button>
</form>

<script>
function toggleParams() {
  var cmd = document.getElementById('command').value;
  var els = document.querySelectorAll('.param');
  for (var i = 0; i < els.length; i++) {
    var show = els[i].getAttribute('data-cmd') === cmd;
    els[i].style.display = show ? 'block' : 'none';
    var inputs = els[i].querySelectorAll('input');
    for (var j = 0; j < inputs.length; j++) {
      inputs[j].disabled = !show;
    }
  }
}
toggleParams();
</script>
</body>
</html>
