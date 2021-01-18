package fr.ensimag.sysd.distr_make

import scala.collection.mutable.{ListBuffer, Queue, HashSet}
import scala.concurrent.duration._
import akka.util.Timeout
import akka.actor.typed.ActorRef
import akka.actor.typed.Behavior
import akka.actor.typed.receptionist.Receptionist
import akka.actor.typed.scaladsl.ActorContext
import akka.actor.typed.scaladsl.Behaviors

import scala.util.Failure
import scala.util.Success

//#frontend
object Frontend {

  sealed trait Event
  private case object Tick extends Event
  private final case class WorkersUpdated(newWorkers: Set[ActorRef[Worker.MakeTask]]) extends Event
  private final case class TransformCompleted(originalText: String, transformedText: String) extends Event
  private final case class JobFailed(why: String, task: Task) extends Event


  def apply(): Behavior[Event] = Behaviors.setup { ctx =>
    Behaviors.withTimers { timers =>
      // subscribe to available workers
      val subscriptionAdapter = ctx.messageAdapter[Receptionist.Listing] {
        case Worker.WorkerServiceKey.Listing(workers) =>
          WorkersUpdated(workers)

      }
      ctx.system.receptionist ! Receptionist.Subscribe(Worker.WorkerServiceKey, subscriptionAdapter)
      val filename = "Makefile"
      var target = ""
      val parser_makefile = new Parser(filename)
      parser_makefile.create_graph(target)
      var taskQueue = Queue[Task]()
      var waitingTasks = HashSet[Task]()
      var taskDone = HashSet[String]()
      ctx.log.info(parser_makefile.root_task.children(0).command)
      ctx.log.info(taskDone(parser_makefile.root_task.command).toString())

      for (task <- taskDone){
        ctx.log.info(task)
      }
      taskQueue += parser_makefile.root_task

      timers.startTimerWithFixedDelay(Tick, Tick, 2.seconds)

      running(ctx, IndexedSeq.empty, jobCounter = 0, taskQueue, waitingTasks, taskDone)
    }
  }

  private def running(ctx: ActorContext[Event], workers: IndexedSeq[ActorRef[Worker.MakeTask]], jobCounter: Int, taskQueue: Queue[Task], waitingTasks: HashSet[Task], taskDone: HashSet[String]): Behavior[Event] =
    Behaviors.receiveMessage {
      case WorkersUpdated(newWorkers) =>
        ctx.log.info("List of services registered with the receptionist changed: {}", newWorkers)
        running(ctx, newWorkers.toIndexedSeq, jobCounter, taskQueue, waitingTasks, taskDone)
      case Tick =>
        val currentTask = choiceTask(taskQueue, waitingTasks, taskDone)
        ctx.log.info(currentTask.command)
        ctx.log.info(taskQueue.isEmpty.toString())
        ctx.log.info(waitingTasks.isEmpty.toString())
        if (workers.isEmpty) {
          ctx.log.warn("Got tick request but no workers available, not sending any work")
          Behaviors.same
        } else if (currentTask.command == ""){
          ctx.log.warn("Waiting for work")
          Behaviors.same
        } else {
          // how much time can pass before we consider a request failed
          implicit val timeout: Timeout = 150.seconds
          val selectedWorker = workers(jobCounter % workers.size)
          ctx.log.info("Sending work for processing to {}", selectedWorker)
          ctx.ask(selectedWorker, Worker.MakeTask(currentTask.command, _)) {
            case Success(transformedText) => TransformCompleted(transformedText.text, transformedText.text)
            case Failure(ex) => JobFailed("Processing timed out", currentTask)
          }
          running(ctx, workers, jobCounter + 1, taskQueue, waitingTasks, taskDone)
        }
      case TransformCompleted(originalText, transformedText) =>
        taskDone += transformedText
        ctx.log.info("Got completed run of: {}", transformedText)
        Behaviors.same

      case JobFailed(why, currentTask) =>
        ctx.log.warn("Run of {} failed. Because: {}", currentTask.command, why)
        taskQueue += currentTask
        Behaviors.same

    }

    def choiceTask(taskQueue: Queue[Task], waitingTasks: HashSet[Task], taskDone: HashSet[String]): Task =
      if (waitingTasks.isEmpty && taskQueue.isEmpty){
        return new Task("", List[Task](), "")
      } else {
        while (!taskQueue.isEmpty){
          val task = taskQueue.dequeue()
          for (child <- task.children){
            taskQueue += child
          }
          if (!taskDone(task.command) && task.children.forall(x => taskDone(x.command))){
            return task
          } else {
            waitingTasks += task
          }
        }
        for (waitingtask <- waitingTasks){
          if (!taskDone(waitingtask.command) && waitingtask.children.forall(x => taskDone(x.command))){
            waitingTasks -= waitingtask
            return waitingtask
          }
        }
        return new Task("", List[Task](), "")
      }

}
