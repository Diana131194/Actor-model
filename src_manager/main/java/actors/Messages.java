package actors;

import akka.actor.ActorRef;
import akka.util.ByteString;

import java.io.File;
import java.io.Serializable;


public class Messages {
  public static final class ConnectUser implements Serializable{
    final String userName;
    final ActorRef actorRef;

    public ConnectUser(String userName, ActorRef actorRef) {
      this.userName = userName;
      this.actorRef = actorRef;
    }
  }
  public static final class DisconnectUser implements Serializable {
    final String userName;

    public DisconnectUser(String userName) {
      this.userName  =userName;
    }
  }
  public static final class AskUserRef implements Serializable {
    final String userName;

    public AskUserRef(String userName) {
      this.userName  =userName;
    }
  }
  public static final class TextMessage implements Serializable {
    final String sender;
    final String text;

    public TextMessage(String sender, String text) {
      this.sender = sender;
      this.text = text;
    }
  }
  public static final class BinaryMessage implements Serializable {
    final String sender;
    final File file;
    final ByteString byteString;

    public BinaryMessage(String sender, File file, ByteString byteString) {
      this.sender = sender;
      this.file = file;
      this.byteString = byteString;
    }
  }
  public static final class BinaryGroupMessage implements Serializable{
    final String sender;
    final File file;
    final ByteString byteString;
    final String groupName;

    public BinaryGroupMessage(String sender, File file, ByteString byteString, String groupName) {
      this.sender = sender;
      this.file = file;
      this.byteString = byteString;
      this.groupName = groupName;
    }
  }

  public static final class BinaryMessagePrint implements Serializable{
    final String sender;
    final String targetPath;

    public BinaryMessagePrint(String sender, String path) {
      this.sender = sender;
      this.targetPath = path;
    }
  }


  public static final class GroupCreate implements Serializable{
    final String sender;
    final String groupName;

    public GroupCreate(String sender, String groupName) {
      this.sender = sender;
      this.groupName = groupName;
    }
  }
  public static final class GroupLeave implements Serializable{
    final String sender;
    final String groupName;

    public GroupLeave(String sender, String groupName) {
      this.sender = sender;
      this.groupName = groupName;
    }
  }
  public static final class GroupSendtext implements Serializable{
    final String sender;
    final String groupName;
    final String text;

    public GroupSendtext(String sender, String groupName, String text) {
      this.sender = sender;
      this.groupName = groupName;
      this.text = text;
    }
  }
  public static final class GroupSendFile implements Serializable{
    final String sender;
    final String groupName;
    final File file;
    final String path;

    public GroupSendFile(String sender, String groupName, File file, String path) {
      this.sender = sender;
      this.groupName = groupName;
      this.file = file;
      this.path = path;
    }
  }

  public static final class GroupUserInvite implements Serializable{
    final String sender;
    final String groupName;
    final String userName;

    public GroupUserInvite(String sender, String groupName, String userName) {
      this.sender = sender;
      this.groupName = groupName;
      this.userName = userName;
    }
  }

  public static final class UserInvite implements Serializable{

    final String groupName;
    final String inviter;

    public UserInvite(String groupName, String inviter) {

      this.groupName = groupName;
      this.inviter = inviter;
    }
  }

  public static final class UserInviteResponse implements Serializable{

    final String response;
    final String sender;
    final String groupName;
    final String inviter;

    public UserInviteResponse(String response, String sender, String groupName, String inviter) {
      this.response = response;
      this.sender = sender;
      this.groupName = groupName;
      this.inviter = inviter;
    }
  }

  public static final class GroupUserRemove implements Serializable{
    final String sender;
    final String groupName;
    final String userName;

    public GroupUserRemove(String sender, String groupName, String userName) {
      this.sender = sender;
      this.groupName = groupName;
      this.userName = userName;
    }
  }

  public static final class GroupUserMute implements Serializable{
    final String sender;
    final String groupName;
    final String userName;
    final Long seconds;

    public GroupUserMute(String sender, String groupName, String userName, Long seconds) {
      this.sender = sender;
      this.groupName = groupName;
      this.userName = userName;
      this.seconds = seconds;
    }
  }

  public static final class GroupUserUnMute implements Serializable{
    final String sender;
    final String groupName;
    final String userName;

    public GroupUserUnMute(String sender, String groupName, String userName) {
      this.sender = sender;
      this.groupName = groupName;
      this.userName = userName;
    }
  }


  public static final class GroupAddCoAdmin implements Serializable{
    final String sender;
    final String groupName;
    final String userName;

    public GroupAddCoAdmin(String sender, String groupName, String userName) {
      this.sender = sender;
      this.groupName = groupName;
      this.userName = userName;
    }
  }

  public static final class GroupRemoveCoAdmin implements Serializable{
    final String sender;
    final String groupName;
    final String userName;

    public GroupRemoveCoAdmin(String sender, String groupName, String userName) {
      this.sender = sender;
      this.groupName = groupName;
      this.userName = userName;
    }
  }
}