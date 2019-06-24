package actors;
import akka.actor.ActorRef;
import akka.actor.ActorSystem;
import akka.actor.Props;

import java.util.Scanner;

public class Main {
	public static void main(String[] args) {
		//creating the system
		ActorSystem system = ActorSystem.create("ChatSystem");
		//creating system actors
		ActorRef user = system.actorOf(Props.create(ChatUser.class), "user");
		/*akka.Main.main(new String[] {
				ChatUser.class.getName()
		});

		 */
		Scanner reader = new Scanner(System.in);
		while(true){
			String text = reader.nextLine();
			user.tell(text,null);

		}

		
	}
	
	
}
