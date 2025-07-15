import akka.actor.{Actor, ActorSystem, Props}

// Message definition
case class ProcessMessage(msg: String)
case object Ack
case object Invalid

// Processing Actor
class ProcessingActor extends Actor {
  def receive: Receive = {
    case ProcessMessage(msg) =>
      if (isValid(msg)) {
        println(s"[${self.path.name}] ✅ Processed: $msg")
        sender() ! Ack
      } else {
        println(s"[${self.path.name}] ❌ Invalid message: $msg")
        sender() ! Invalid
      }

    case _ =>
      println(s"[${self.path.name}] ❌ Unknown message")
  }

  // Validation logic
  def isValid(msg: String): Boolean = msg.nonEmpty && msg.matches("^[a-zA-Z0-9 ]+$")
}

// Main App
object ProcessingApp extends App {
  val system = ActorSystem("MessageSystem")
  val processor = system.actorOf(Props[ProcessingActor], "processor")

  // Sending test messages
  processor ! ProcessMessage("Hello Akka")
  processor ! ProcessMessage("")                // Invalid
  processor ! ProcessMessage("$$Invalid$$123")  // Invalid
  processor ! ProcessMessage("Akka123")
}




// Commands
case class ProcessMessage(msg: String)
case class Retry(msg: String, attempt: Int = 1)
case object Ack
case object Invalid

// Events
case class MessageProcessed(msg: String)


import akka.actor._
import akka.persistence._
import scala.concurrent.duration._

class PersistentProcessingActor(maxRetries: Int = 3) extends PersistentActor {
  override def persistenceId: String = "processor-1"

  // Internal state
  var processedMessages: List[String] = Nil

  override def receiveCommand: Receive = {
    case ProcessMessage(msg) =>
      if (isValid(msg)) {
        persist(MessageProcessed(msg)) { evt =>
          updateState(evt)
          println(s"[${self.path.name}] ✅ Processed: $msg")
          sender() ! Ack
        }
      } else {
        println(s"[${self.path.name}] ❌ Invalid: $msg → retrying")
        scheduleRetry(msg, 1)
      }

    case Retry(msg, attempt) =>
      if (attempt > maxRetries) {
        println(s"[${self.path.name}] ❌ Failed after $maxRetries retries: $msg")
        sender() ! Invalid
      } else if (isValid(msg)) {
        persist(MessageProcessed(msg)) { evt =>
          updateState(evt)
          println(s"[${self.path.name}] 🔁 Retry success ($attempt): $msg")
          sender() ! Ack
        }
      } else {
        println(s"[${self.path.name}] 🔁 Retry $attempt failed: $msg")
        scheduleRetry(msg, attempt + 1)
      }
  }

  override def receiveRecover: Receive = {
    case evt: MessageProcessed => updateState(evt)
  }

  def updateState(evt: MessageProcessed): Unit = {
    processedMessages = evt.msg :: processedMessages
  }

  def isValid(msg: String): Boolean = msg.nonEmpty && msg.matches("^[a-zA-Z0-9 ]+$")

  def scheduleRetry(msg: String, attempt: Int): Unit = {
    import context.dispatcher
    context.system.scheduler.scheduleOnce(2.seconds, self, Retry(msg, attempt))
  }
}



object PersistentApp extends App {
  val system = ActorSystem("PersistentSystem")
  val processor = system.actorOf(Props(new PersistentProcessingActor()), "processor")

  processor ! ProcessMessage("Akka Rocks")
  processor ! ProcessMessage("")                      // Will retry
  processor ! ProcessMessage("!@#@ Invalid Format")    // Will retry
  processor ! ProcessMessage("ValidAgain123")
}

