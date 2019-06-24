package actors;


import java.util.ArrayList;

import java.io.File;
import java.util.Arrays;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import actors.Messages;
import akka.actor.ActorRef;
import akka.actor.ActorSelection;
import akka.actor.Props;
//import akka.compat.Future;
import akka.japi.Pair;
import akka.pattern.Patterns;
import akka.routing.BroadcastRoutingLogic;
import akka.routing.RoundRobinGroup;
import akka.routing.RoundRobinRoutingLogic;
import akka.routing.Routee;
import akka.routing.Router;
import akka.util.Timeout;
import scala.concurrent.Await;
import scala.concurrent.Future;

import java.lang.Object;
import java.time.Duration;

import akka.actor.ActorSystem;
import akka.actor.AbstractActor;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

public class ChatManager extends AbstractActor {

	//creating the manager system
	//ActorSystem system = ActorSystem.create("Manager");
	//initialize the ActorRef of the manager actor
	//ActorRef Manager = null;
	//initialize a map that will hold all the users and their ActorRef that are connected to the system
	Map<String,ActorRef> usersRef = new HashMap<String,ActorRef>();
	//initialize a list that will hold all the users that need to respond to invitation to a specific gruoup
	Map<String, ArrayList<String>> invited_users = new HashMap<String, ArrayList<String>>();
	//initialize a map that will hold all the groups and their router that will hold the users that are in the group
	Map<String, Router> groups = new HashMap<String, Router>();
	//initialize a map that will hold a user and all of the groups he is in
	Map<String, ArrayList<String>> groups_per_user = new HashMap<String, ArrayList<String>>();
	////initialize a map that will hold all the groups and their admins
	Map<String, String> group_admin = new HashMap<String, String>();
	//initialize a map that will hold all the groups and their co-admins
	Map<String, ArrayList<String>> group_co_admins = new HashMap<String, ArrayList<String>>();
	//initialize a map that will hold all the groups and their users
	Map<String, ArrayList<String>> group_users = new HashMap<String, ArrayList<String>>();
	//initialize a map that will hold all the groups and their muted users
	Map<String, ArrayList<Pair<String, Long>>> group_muted_users = new HashMap<String, ArrayList<Pair<String, Long>>>();








	public ChatManager(){
		//create the manager actor
		//Manager = system.actorOf(Props.create(ChatManager.class), "myactorManager");
		//read_write = getContext().actorOf(Props.create(ReaderWriter.class), "readerWriter");;

	}

	@Override
	public Receive createReceive() {
		return receiveBuilder()

				//user is asking to connect to the system
				.match(Messages.ConnectUser.class, s -> {
					//case that username is already in use
					if(usersRef.containsKey(s.userName)){
						getSender().tell(s.userName + " is in use!", getSelf());
					}
					else {
						usersRef.put(s.userName, s.actorRef);
						ArrayList<String> groups_of_user = new ArrayList<>();
						groups_per_user.put(s.userName, groups_of_user);
						getSender().tell(s.userName + " has connected successfully!", getSelf());
					}

				})
				//user is asking to disconnect from the system
				.match(Messages.DisconnectUser.class,  s -> {
					//search the user in the map and delete it from it
					if(usersRef.containsKey(s.userName)){
						ArrayList<String> groups_of_user = groups_per_user.get(s.userName);
						groups_of_user.parallelStream().forEach(a -> getSelf().tell(new Messages.GroupLeave(s.userName, a), getSender()));
						usersRef.remove(s.userName);
						getSender().tell(s.userName + " has been disconnected successfully!", getSelf());
					}
					else {
						getSender().tell(s.userName + "is not connected", getSelf());
					}

				})
				//some user asking the ActorRef of another user to send him a message
				.match(Messages.AskUserRef.class,  s -> {
					//check if user is connected
					if(usersRef.containsKey(s.userName)){
						getSender().tell(usersRef.get(s.userName), getSelf());
					}
					else {
						getSender().tell(s.userName + " does not exist!", getSelf());
					}
				})

				//a user is asking to create a group
				.match(Messages.GroupCreate.class, s -> {
					//case that the group is already exists
					if(groups.containsKey(s.groupName)){
						getSender().tell(s.groupName + "  already exists!", getSelf());
					}
					else{
						//create an empty list of this group member, until new members will be added to the group
						ArrayList<String> coAdmins = new ArrayList<>();
						group_co_admins.put(s.groupName, coAdmins);
						ArrayList<String> userList = new ArrayList<>();
						group_users.put(s.groupName, userList);
						ArrayList<Pair<String, Long>> mutedList = new ArrayList<>();
						group_muted_users.put(s.groupName, mutedList);
						ArrayList<String> invitedList = new ArrayList<>();
						invited_users.put(s.groupName, invitedList);
						ArrayList<Routee> routees = new ArrayList<Routee>();
						Router router = new Router(new BroadcastRoutingLogic(), routees);


						groups.put(s.groupName, router.addRoutee(getSender()));
						group_admin.put(s.groupName, s.sender);
						//if the asker is already in some group, just add a new group to his list
						if(groups_per_user.containsKey(s.sender)){
							ArrayList<String> group = groups_per_user.get(s.sender);
							//add the group
							group.add(s.groupName);
							//update the new list
							groups_per_user.replace(s.sender, group);
						}
						//this is the only group that the asker will be a part of for now, so make a new list
						else{
							ArrayList<String> group = new ArrayList<String>();
							group.add(s.groupName);
							groups_per_user.put(s.sender, group);
						}

						getSender().tell("[" + new Date().toString() + "]" + "[" + s.groupName + "]" + "[" + s.sender + "] " + s.groupName + " created successfully!", getSelf());
					}

				})
				//a user is asking to leave a specific group
				.match(Messages.GroupLeave.class, s -> {
					//get the list of users and muted users to check if the sender is in them
					ArrayList<String> users = group_users.get(s.groupName);
					ArrayList<Pair<String, Long>> muted = group_muted_users.get(s.groupName);
					ArrayList<String> muted_users = muted.stream().map( a -> a.first()).collect(Collectors.toCollection(ArrayList::new));;
					ArrayList<String> co_admins = group_co_admins.get(s.groupName);
					String admin = group_admin.get(s.groupName);
					//case that the asker is the admin of the group
					if(admin.equals(s.sender)){
						(groups.get(s.groupName)).route("[" + (new Date().toString()) + "]" + "[" + s.groupName + "]" + "[" + s.sender + "] " + s.groupName + " admin has closed " + s.groupName + "!", getSelf());
						groups.remove(s.groupName);
						group_admin.remove(s.groupName);
						group_co_admins.remove(s.groupName);
						group_users.remove(s.groupName);
						group_muted_users.remove(s.groupName);
						//get the group list that the asker is part of
						ArrayList<String> to_remove = groups_per_user.get(s.sender);
						//remove this group from his group list
						to_remove.remove(s.groupName);
						groups_per_user.replace(s.sender, to_remove);
					}
					//case that the asker is a co_admin of the group
					else if(co_admins.contains(s.sender)){
						co_admins.remove(s.sender);
						group_co_admins.replace(s.groupName, co_admins);
						//get the asker out of the routees list of the group
						Router rout = groups.get(s.groupName);
						Router router2 = rout.removeRoutee(getSender());
						groups.replace(s.groupName, router2);
						//get the group list that the asker is part of
						ArrayList<String> to_remove = groups_per_user.get(s.sender);
						//remove this group from his group list
						to_remove.remove(s.groupName);
						groups_per_user.replace(s.sender, to_remove);
						(groups.get(s.groupName)).route(s.sender + " has left " + s.groupName + "!", getSelf());
					}
					//case that the asker is a user or a muted_user
					else if(users.contains(s.sender) || muted_users.contains(s.sender)){
						//remove user from users list
						if(users.contains(s.sender)){
							users.remove(s.sender);
							//update the users of the group
							group_users.replace(s.groupName, users);

						}
						else {
							ArrayList<Pair<String,Long>> new_muted = muted.stream().filter( a -> !a.first().equals(s.sender)).collect(Collectors.toCollection(ArrayList::new));;
							//update the muted_users of the group
							group_muted_users.replace(s.groupName, new_muted);
						}
						//get the asker out of the routees list of the group
						Router rout = groups.get(s.groupName);
						Router router2 = rout.removeRoutee(getSender());
						groups.replace(s.groupName, router2);
						//get the group list that the asker is part of
						ArrayList<String> to_remove = groups_per_user.get(s.sender);
						//remove this group from his group list
						to_remove.remove(s.groupName);
						groups_per_user.replace(s.sender, to_remove);
						(groups.get(s.groupName)).route(s.sender + " has left " + s.groupName + "!", getSelf());


					}
					else {
						getSender().tell(s.sender + " is not in " + s.groupName + "!", getSelf());
					}

				})
				//case of a group text message
				.match(Messages.GroupSendtext.class, s -> {
					//get the muted users list
					ArrayList<Pair<String, Long>> muted = group_muted_users.get(s.groupName);
					ArrayList<String> muted_users = muted.stream().map( a -> a.first()).collect(Collectors.toCollection(ArrayList::new));;
					//case that the group does not exist
					if(!groups.containsKey(s.groupName)){
						getSender().tell(s.groupName + "does not exist!", getSelf());
					}
					else if(!muted_users.contains(s.sender)){
						(groups.get(s.groupName)).route("[" + (new Date()).toString() + "]" + "[" + s.groupName + "]" + "[" + s.sender + "] " + s.text, getSelf());
					}
				})
				//case of agroup  file message
				.match(Messages.BinaryGroupMessage.class, s -> {
					//get all the users, muted users, co-admins and the admin of the group
					ArrayList<String> users = group_users.get(s.groupName);
					ArrayList<Pair<String, Long>> muted = group_muted_users.get(s.groupName);
					ArrayList<String> muted_users = muted.stream().map( a -> a.first()).collect(Collectors.toCollection(ArrayList::new));
					ArrayList<String> co_admins = group_co_admins.get(s.groupName);
					String admin = group_admin.get(s.groupName);
					//case that the group does not exist
					if(!groups.containsKey(s.groupName)){
						getSender().tell(s.groupName + "does not exist!", getSelf());
					}
					//the sender is not part of the group
					else if (!users.contains(s.sender) && !co_admins.contains(s.sender) && !muted_users.contains(s.sender) && ! admin.equals(s.sender) ){
						getSender().tell("You are not part of " + s.groupName + "!", getSelf());
					}
					//case that user is muted
					else if(muted_users.contains(s.sender)){
						//get the Pair that contains the sender and the number of seconds he has been muted
						ArrayList<Pair<String, Long>> sender = muted.stream().filter( a -> a.first().equals(s.sender)).collect(Collectors.toCollection(ArrayList::new));;
						Long seconds = sender.get(0).second();
						getSender().tell("[" + (new Date()).toString() + "] " + "[" + s.groupName + "] " + "[" + s.sender + "] " + "You are muted for " +  seconds.toString() + " seconds in " + s.groupName + "!"  , getSelf());
					}
					else if(!s.file.exists()) {
						getSender().tell(s.file.getPath() + " does not exist!", getSelf());

					}
					//if the sender exists in group and not muted, send the file to all group member
					else {
						(groups.get(s.groupName)).route(s , getSelf());
					}

				})
				//case of a group invite
				.match(Messages.GroupUserInvite.class, s -> {
					//get the co-admins and the admin of the group
					ArrayList<String> co_admins = group_co_admins.get(s.groupName);
					String admin = group_admin.get(s.groupName);
					//get the muted users and regular users of the group
					ArrayList<String> users = group_users.get(s.groupName);
					ArrayList<Pair<String, Long>> muted = group_muted_users.get(s.groupName);
					ArrayList<String> muted_users = muted.stream().map( a -> a.first()).collect(Collectors.toCollection(ArrayList::new));;
					//the group does not exist
					if(!groups.containsKey(s.groupName)){
						getSender().tell(s.groupName + " does not exist!", getSelf());
					}
					//if the inviter isn't the admin or co-admin of the group, an error message will be printed
					else if(!co_admins.contains(s.sender) && !admin.equals(s.sender)){
						getSender().tell("You are neither an admin nor a co-admin of " + s.groupName , getSelf());
					}
					//if the invited user don't even exist, an error message will be printed
					else if(!usersRef.containsKey(s.userName)){
						getSender().tell(s.userName + " does not exist!" , getSelf());
					}
					//if the invited user is already part of the gruop, an error mwssage will be printed
					else if(users.contains(s.userName) || muted_users.contains(s.userName)) {
						getSender().tell(s.userName + " is already in " + s.groupName + "!", getSelf());
					}
					else{
						ActorRef invitedUser = usersRef.get(s.userName);
						//get the invited list of the group
						ArrayList<String> group_invited = invited_users.get(s.groupName);
						//add the current invited user to the list
						group_invited.add(s.userName);
						//replace the old list eith the new list
						invited_users.replace(s.groupName, group_invited);
						invitedUser.tell(new Messages.UserInvite(s.groupName, s.sender), getSelf());
					}


				})
				//the response of an invited user to a group
				.match(Messages.UserInviteResponse.class, s -> {
					//check if there are any invited users to this group
					if (invited_users.containsKey(s.groupName)){
						ArrayList<String> invited = invited_users.get(s.groupName);
						//check if the invitor is really waiting on this user to response to the invitation
						if(invited.contains(s.sender)){
							if(s.response.equals("yes")){
								ArrayList<String> group_invited = invited_users.get(s.groupName);
								//remove the current invited user from the list
								group_invited.remove(s.sender);
								//replace the old list with the new list
								invited_users.replace(s.groupName, group_invited);
								//get the users list of thi group
								ArrayList<String> users = group_users.get(s.groupName);
								//add the current invited user to the list
								users.add(s.sender);
								//replace the old list with the new list
								invited_users.replace(s.groupName, users);

								//if the invited user is already in some group, just add a new group to his list
								if(groups_per_user.containsKey(s.sender)){
									ArrayList<String> group = groups_per_user.get(s.sender);
									//add the group
									group.add(s.groupName);
									//update the new list
									groups_per_user.replace(s.sender, group);
								}
								//this is the only group that the invited user will be a part of for now, so make a new list
								else{
									ArrayList<String> group = new ArrayList<String>();
									group.add(s.groupName);
									groups_per_user.put(s.sender, group);
								}

								//add the invited user (that have accepted the invitatrion) to the group of routees
								Router rout = groups.get(s.groupName);
								Router router2 = rout.addRoutee(getSender());
								//replace the router oh this group with a new router
								groups.replace(s.groupName, router2);
								//the welcome message will be sent from the inviter
								ActorRef inviter = usersRef.get(s.inviter);
								getSender().tell("[" + (new Date()).toString() + "] " + "[" + s.groupName + "] " + "[" + s.inviter + "] " + "Welcome to " + s.groupName + "!",inviter);
							}
							else {
								ArrayList<String> group_invited = invited_users.get(s.groupName);
								//remove the current invited user from the list
								group_invited.remove(s.sender);
								//replace the old list with the new list
								invited_users.replace(s.groupName, group_invited);
							}

						}
					}
				})
				//case of removing a user from a group
				.match(Messages.GroupUserRemove.class, s -> {
					//get the co-admins and the admin of a group
					ArrayList<String> co_admins = group_co_admins.get(s.groupName);
					String admin = group_admin.get(s.groupName);
					//get the muted users and regular users of the group
					ArrayList<String> users = group_users.get(s.groupName);
					ArrayList<Pair<String, Long>> muted = group_muted_users.get(s.groupName);
					ArrayList<String> muted_users = muted.stream().map( a -> a.first()).collect(Collectors.toCollection(ArrayList::new));
					//if the group name doesn't exists an error will be printed
					if(!groups.containsKey(s.groupName)){
						getSender().tell(s.groupName + " does not exist!", getSelf());
					}
					//if the user not even connected an error will be printed
					else if(!usersRef.containsKey(s.userName)){
						getSender().tell(s.userName + " does not exist!", getSelf());
					}
					//if the asker is not a co-admin or the admin of the group
					else if(!co_admins.contains(s.sender) && !admin.equals(s.sender)){
						getSender().tell("You are neither an admin nor a co-admin of " + s.groupName + "!" , getSelf());
					}
					else if(co_admins.contains(s.userName) || muted.contains(s.userName) || users.contains(s.userName)){
						//the case that the remover is the admin, in that case he can remove a co-admin also
						if (admin.equals(s.sender)){
							//check if the user to remove is a co-admin
							if(co_admins.contains(s.userName)){
								co_admins.remove(s.userName);
								//replace the old co-admins with the new co-admins
								group_co_admins.replace(s.groupName, co_admins);
							}
						}
						//check if the user to remove is a regular user
						if(users.contains(s.userName)){
							users.remove(s.userName);
							//replace the old users with the new users
							group_users.replace(s.groupName, users);
						}
						//check if the user to remove is a muted user
						if(muted_users.contains(s.userName)){
							//remove the user name from the muted list
							ArrayList<Pair<String,Long>> new_muted = muted.stream().filter( a -> !a.first().equals(s.userName)).collect(Collectors.toCollection(ArrayList::new));
							//replace the old muted users list with the new muted users list
							group_muted_users.replace(s.groupName, new_muted);
						}

						//get the asker out of the routees list of the group
						Router rout = groups.get(s.groupName);
						//get the actor of the user that is going to be removed
						ActorRef to_remove = usersRef.get(s.userName);
						Router router2 = rout.removeRoutee(to_remove);
						//replace the old router with the new router without the removed user
						groups.replace(s.groupName, router2);

						//get the group list that the asker is part of
						ArrayList<String> remove = groups_per_user.get(s.userName);
						//remove this group from his group list
						remove.remove(s.groupName);
						groups_per_user.replace(s.userName, remove);

						//get the Actor ref of the user thar removing another user
						ActorRef asker = usersRef.get(s.sender);
						to_remove.tell("[" + (new Date()).toString() + "] " + "[" + s.groupName + "] " + "[" + s.sender + "] " + "You have been removed from " + s.groupName + " by " + s.sender + "!", asker);
					}

				})
				//case of a user mute
				.match(Messages.GroupUserMute.class, s -> {
					//get the co-admins and the admin of a group
					ArrayList<String> co_admins = group_co_admins.get(s.groupName);
					String admin = group_admin.get(s.groupName);
					//get the muted users and regular users of the group
					ArrayList<String> users = group_users.get(s.groupName);
					ArrayList<Pair<String, Long>> muted = group_muted_users.get(s.groupName);
					//if the group name doesn't exists an error will be printed
					if(!groups.containsKey(s.groupName)){
						getSender().tell(s.groupName + " does not exist!", getSelf());
					}
					//if the user not even connected an error will be printed
					else if(!usersRef.containsKey(s.userName)){
						getSender().tell(s.userName + " does not exist!", getSelf());
					}
					//if the asker is not a co-admin or the admin of the group
					else if(!co_admins.contains(s.sender) && !admin.equals(s.sender)){
						getSender().tell("You are neither an admin nor a co-admin of " + s.groupName + "!" , getSelf());
					}
					//the user is in group
					else if (users.contains(s.userName)){
						//send after s.seconds seconds a message to the manager that will change the muted_user to regular user
						this.getContext().getSystem().scheduler().scheduleOnce(Duration.ofSeconds(s.seconds),
								getSelf(), new Messages.GroupUserUnMute(s.sender, s.groupName, s.userName), this.getContext().getSystem().dispatcher(), null);
						//make a new pair that will hold the muted user name and the seconds he is muted
						Pair<String, Long> new_muted = new Pair<String, Long>(s.userName, s.seconds);
						//add this user to the muted user list
						muted.add(new_muted);
						//replace the old muted users list with the new one
						group_muted_users.replace(s.groupName, muted);
						//remove this user from the regular users list
						users.remove(s.userName);
						group_users.replace(s.groupName, users);
						//get the actor ref of the muted user
						ActorRef muted_user = usersRef.get(s.userName);
						muted_user.tell("[" + (new Date()).toString() + "] " + "[" + s.groupName + "] " + "[" + s.sender + "] " + "You have been muted for " + s.seconds.toString() + " seconds in " + s.groupName + " by " + s.sender + "!", getSender());
					}
				})
				//a message that alerts that the muting time of a muted user is over
				.match(Messages.GroupUserUnMute.class, s -> {
					//get the admin, co-admins, users and the muted users lists
					ArrayList<String> co_admins = group_co_admins.get(s.groupName);
					String admin = group_admin.get(s.groupName);
					ArrayList<String> users = group_users.get(s.groupName);
					ArrayList<Pair<String, Long>> muted = group_muted_users.get(s.groupName);
					ArrayList<String> muted_users = muted.stream().map( a -> a.first()).collect(Collectors.toCollection(ArrayList::new));
					//if the group name doesn't exists an error will be printed
					if(!groups.containsKey(s.groupName)){
						getSender().tell(s.groupName + " does not exist!", getSelf());
					}
					//if the user not even connected an error will be printed
					else if(!usersRef.containsKey(s.userName)){
						getSender().tell(s.userName + " does not exist!", getSelf());
					}
					//if the asker is not a co-admin or the admin of the group
					else if(!co_admins.contains(s.sender) && !admin.equals(s.sender)){
						getSender().tell("You are neither an admin nor a co-admin of " + s.groupName + "!" , getSelf());
					}
					//check if the user is in the muted list and if so get him out of there
					else if(muted_users.contains(s.userName)) {
						//update the new muted users list (without the user that is un muted now)

						ArrayList<Pair<String,Long>> new_muted = muted.stream().filter( a -> !a.first().equals(s.userName)).collect(Collectors.toCollection(ArrayList::new));
						group_muted_users.replace(s.groupName, new_muted);
						//add this user to the regular users list
						users.add(s.userName);
						//replace the old users list with the new ine
						group_users.replace(s.groupName, users);
						//get the actorRef of the user than is un muted now
						ActorRef unmuted_user = usersRef.get(s.userName);
						unmuted_user.tell("[" + (new Date()).toString() + "] " + "[" + s.groupName + "] " + "[" + s.sender + "] " + "You have been unmuted in " + s.groupName + " by " + s.sender, getSelf());
					}

				})
				//an admin of a group promotes a user to a co-admin
				.match(Messages.GroupAddCoAdmin.class, s -> {
					//get the admin, co-admins and users lists
					ArrayList<String> co_admins = group_co_admins.get(s.groupName);
					String admin = group_admin.get(s.groupName);
					ArrayList<String> users = group_users.get(s.groupName);
					//if the group name doesn't exists an error will be printed
					if(!groups.containsKey(s.groupName)){
						getSender().tell(s.groupName + " does not exist!", getSelf());
					}
					//if the user not even connected an error will be printed
					else if(!usersRef.containsKey(s.userName)){
						getSender().tell(s.userName + " does not exist!", getSelf());
					}
					//if the asker is not a co-admin or the admin of the group
					else if(!admin.equals(s.sender)){
						getSender().tell("You are not an admin of " + s.groupName + "!" , getSelf());
					}
					//if the invited co-admin is part of the users promote him to co-admin
					else if(users.contains(s.userName)){
						users.remove(s.userName);
						//update the users list
						group_users.replace(s.groupName, users);
						//add the user to the co-admins list
						co_admins.add(s.userName);
						//update the co-admins of this group
						group_co_admins.replace(s.groupName, co_admins);
						//get the actor ref of the promoted user to send him a message
						ActorRef promoted = usersRef.get(s.userName);
						promoted.tell("[" + (new Date()).toString() + "] " + "[" + s.groupName + "] " + "[" + s.sender + "] " + "You have been promoted to co-admin in " + s.groupName, getSelf());
					}
				})
				//an admin demote a co-admin to be a user
				.match(Messages.GroupRemoveCoAdmin.class, s -> {
					//get the admin, co-admins and users lists
					ArrayList<String> co_admins = group_co_admins.get(s.groupName);
					String admin = group_admin.get(s.groupName);
					ArrayList<String> users = group_users.get(s.groupName);
					//if the group name doesn't exists an error will be printed
					if(!groups.containsKey(s.groupName)){
						getSender().tell(s.groupName + " does not exist!", getSelf());
					}
					//if the user not even connected an error will be printed
					else if(!usersRef.containsKey(s.userName)){
						getSender().tell(s.userName + " does not exist!", getSelf());
					}
					//if the asker is not a co-admin or the admin of the group
					else if(!admin.equals(s.sender)){
						getSender().tell("You are not an admin of " + s.groupName + "!" , getSelf());
					}
					//check if the demoted is in co-admins list and if yes move him to the users list
					else if(co_admins.contains(s.userName)){
						co_admins.remove(s.userName);
						//update the co-admins of this group
						group_co_admins.replace(s.groupName, co_admins);
						//add the demoted to the users list
						users.add(s.userName);
						//update the users list
						group_users.replace(s.groupName, users);
						//get the actor ref of the demoted to send him a message
						ActorRef demoted = usersRef.get(s.userName);
						demoted.tell("[" + (new Date()).toString() + "] " + "[" + s.groupName + "] " + "[" + s.sender + "] " + "You have been demoted to user in " + s.groupName, getSelf());
					}
				})

				.build();



	}
}
