package actors;
import akka.Done;
import akka.NotUsed;
import akka.actor.AbstractActor;
import akka.actor.ActorRef;
import akka.actor.ActorSelection;
import akka.pattern.*;
import akka.actor.ActorSystem;
import akka.actor.Props;
import akka.stream.ActorMaterializer;
import akka.stream.IOResult;
import akka.stream.Materializer;
import akka.stream.impl.io.FileSink;
import akka.stream.javadsl.FileIO;
import akka.stream.javadsl.Sink;
import akka.stream.javadsl.Source;
import akka.util.ByteString;
import akka.util.Timeout;
import scala.concurrent.Await;
import scala.concurrent.Future;
import scala.concurrent.duration.Duration;

import java.io.File;
import java.io.FileOutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.Date;
import java.util.LinkedList;
import java.util.Queue;
import java.util.concurrent.CompletionStage;


import static java.util.concurrent.TimeUnit.SECONDS;

public class ChatUser extends AbstractActor{

	//holds current actor user
	private String userName = "User";
	//queue to handle pending invites.
	private Queue<Messages.UserInvite> invitesQueue = new LinkedList<>();

	public ChatUser(){
		//this.userName = userName;
	}


	@Override
	public Receive createReceive() {
		return receiveBuilder()
				//receive text message.
				.match(Messages.TextMessage.class, s -> {
					System.out.println("[" + new Date().toString() + "]" + "[user][" + s.sender + "] " + s.text);
					//case that username is already in use

				})
				.match(Messages.BinaryMessage.class, s -> {
					//receive text file.
					//creates a temporary file to prevent repetition.
					File temp = File.createTempFile("copy of " + s.file.getName(),"");
					//creates stream from file.
					FileOutputStream outputStream = new FileOutputStream(temp);
					//write to arry.
					outputStream.write(s.byteString.toArray());
					System.out.println("[" + new Date().toString() + "]" + "[user][" + s.sender + "] "+ "file received: " + temp.getAbsolutePath());
				})
				//user invite request.
				.match(Messages.UserInvite.class, s -> {

					System.out.println("[" + new Date().toString() + "] You have been invited to "+ s.groupName+ ", accept?");
					invitesQueue.add(s);
				})

				//binary group search.
				.match(Messages.BinaryGroupMessage.class, s -> {
					File temp = File.createTempFile("copy of " + s.file.getName(),"");
					FileOutputStream outputStream = new FileOutputStream(temp);
					outputStream.write(s.byteString.toArray());
					System.out.println("[" + new Date().toString() + "]" + "["+s.groupName+"][" + s.sender + "] "+ "file received: " + temp.getAbsolutePath());

				})
				//text

				.match(String.class, s -> {
					String[] result = s.split("\\s");
					ActorSelection chatManager = getContext().actorSelection(
							"akka.tcp://ChatSystem@127.0.0.1:3553/user/Manager");

					switch(result[0]){
						//response for group invite.
						case "yes":
							//check if theres a pending invite.
							if(!invitesQueue.isEmpty()){
								Messages.UserInvite invite = invitesQueue.remove();
								chatManager.tell(new Messages.UserInviteResponse("yes", userName,invite.groupName,invite.inviter), self());
							}

							break;

						//response for group invite.
						case "no":
							if(!invitesQueue.isEmpty()){
								Messages.UserInvite invite = invitesQueue.remove();
								chatManager.tell(new Messages.UserInviteResponse("no", userName,invite.groupName,invite.inviter), self());
							}

							break;
						case "/user":
							switch (result[1]) {
								case "connect":
									final Timeout connectTimeout = new Timeout(Duration.create(1, SECONDS));
									Future<Object> connectRt = Patterns.ask(chatManager, new Messages.ConnectUser(result[2],getSelf()), 1000);
									try {
										//send and await for response from manager.
										String string = (String) Await.result(connectRt, connectTimeout.duration());
										System.out.println(string);
										//update user name.
										userName = result[2];

									}catch(Exception e)
									{
										//no connection server is offline.
										System.out.println("Server is offline!");
									}
									break;

								case "disconnect":
									final Timeout disconnectTimeout = new Timeout(Duration.create(1, SECONDS));
									Future<Object> disconnectRt = Patterns.ask(chatManager, new Messages.DisconnectUser(userName), 1000);
									try {
										//send and await for response from manager.
										String string = (String)Await.result(disconnectRt, disconnectTimeout.duration());
										System.out.println(string);

									}catch(Exception e)
									{

										System.out.println("Server is offline!");
									}
									break;
								case "text":
									final Timeout timeout = new Timeout(Duration.create(1, SECONDS));
									Future<Object> rt = Patterns.ask(chatManager, new Messages.AskUserRef(result[2]), 1000);
									try {
										//get from manager reference for target actor.
										ActorRef actorRef = (ActorRef) Await.result(rt, timeout.duration());
										//get the rest of the IO message.
										String message = String.join(" ", Arrays.copyOfRange(result, 3, result.length));
										//send to target actor.
										actorRef.tell(new Messages.TextMessage(userName,message), self());
									} catch (Exception e) {
										System.out.println(e);
									}

									break;

								case "file":
									final Timeout timeout2 = new Timeout(Duration.create(1, SECONDS));
									Future<Object> rt2 = Patterns.ask(chatManager, new Messages.AskUserRef(result[2]), 1000);

									try {
										//get from manager reference for target actor.
										ActorRef actorRef2 = (ActorRef) Await.result(rt2, timeout2.duration());
										final Path file = Paths.get(result[3]);
										byte[] fileData = Files.readAllBytes(Paths.get(result[3]));
										//send to actor target.
										actorRef2.tell(new Messages.BinaryMessage(userName,file.toFile(),ByteString.fromArray(fileData)), self());
									} catch (Exception e) {
										System.out.println(e);
									}
									break;


							}
							break;
						case "/group":
							switch (result[1]){
								case "create":
									chatManager.tell(new Messages.GroupCreate(userName,result[2]), self());
									break;

								case "leave":
									chatManager.tell(new Messages.GroupLeave(userName,result[2]), self());
									break;

								case "send":
									switch (result[2]) {
										case "text":
											//get the rest of the IO message.
											String message = String.join(" ", Arrays.copyOfRange(result, 4, result.length));
											chatManager.tell(new Messages.GroupSendtext(userName, result[3], message), self());

											break;
										case "file":


											chatManager.tell(new Messages.BinaryGroupMessage(userName,Paths.get(result[4]).toFile(),
													ByteString.fromArray(Files.readAllBytes(Paths.get(result[4]))),result[3]),self() );

											break;
									}

									break;

								case "user":
									switch (result[2]){
										case "invite":
											chatManager.tell(new Messages.GroupUserInvite(userName,result[3],result[4]), self());
											break;
										case "remove":
											chatManager.tell(new Messages.GroupUserRemove(userName,result[3],result[4]), self());

											break;
										case "mute":
											chatManager.tell(new Messages.GroupUserMute(userName,result[3],result[4],Long.parseLong(result[5])), self());
											break;
										case "unmute":
											chatManager.tell(new Messages.GroupUserUnMute(userName,result[3],result[4]), self());
											break;

									}
								case "coadmin":
									switch (result[2]) {
										case "add":
											chatManager.tell(new Messages.GroupAddCoAdmin(userName, result[3], result[4]), self());
											break;
										case "remove":
											chatManager.tell(new Messages.GroupRemoveCoAdmin(userName, result[3], result[4]), self());

											break;
									}

									break;

							}
							break;
						default:
							System.out.print(s);

							break;



					}
				})
				.build();
	}
}