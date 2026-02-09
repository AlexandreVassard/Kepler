<?php

$secret = "changeme"; // Must match rcon.secret in server.ini

sendRcon("127.0.0.1", "12309", build("hotel_alert", array(
   "secret" => $secret,
   "message" => "Hello, World!",
   "sender" => "Alex"
)));

sendRcon("127.0.0.1", "12309", build("hotel_alert", array(
   "secret" => $secret,
   "message" => "Hello, World!"
)));

sendRcon("127.0.0.1", "12309", build("refresh_looks", array(
   "secret" => $secret,
   "userId" => "1"
)));

sendRcon("127.0.0.1", "12309", build("refresh_hand", array(
   "secret" => $secret,
   "userId" => "1"
)));

sendRcon("127.0.0.1", "12309", build("refresh_club", array(
   "secret" => $secret,
   "userId" => "1"
)));

sendRcon("127.0.0.1", "12309", build("refresh_credits", array(
   "secret" => $secret,
   "userId" => "1"
)));

function build($header, $parameters) {
  $message = "";
  $message .= pack('N', strlen($header));
  $message .= $header;
  $message .= pack('N', count($parameters));
  
  foreach ($parameters as $key => $value) {
    $message .= pack('N', strlen($key));
    $message .= $key;
 
    $message .= pack('N', strlen($value));
    $message .= $value;
  }
   
  $buffer = "";
  $buffer .= pack('N', strlen($message));
  $buffer .= $message;
  return $buffer;
}

function sendRcon($ip, $port, $command) {
    $socket = @socket_create(AF_INET, SOCK_STREAM, getprotobyname('tcp'));
    @socket_connect($socket, $ip, $port);
    @socket_send($socket, $command, strlen($command), MSG_DONTROUTE);    
    @socket_close($socket);
} 

?>
