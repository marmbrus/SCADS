package edu.berkeley.cs.scads.test
import edu.berkeley.cs.scads.storage.{ScadsCluster, SpecificNamespace, TestScalaEngine}
import org.junit.runner.RunWith
import org.scalatest.junit.JUnitRunner
import org.scalatest.matchers.ShouldMatchers
import org.scalatest.{WordSpec, Spec}
import edu.berkeley.cs.scads.comm._

@RunWith(classOf[JUnitRunner])
class QuorumProtSpec extends WordSpec with ShouldMatchers {

  MessageHandlerFactory.creatorFn = () => new TestMessageHandler()

  val messageHandler : TestMessageHandler = MessageHandler.instance match {
       case g2: TestMessageHandler => g2
       case _ => throw new ClassCastException
    }

  val storageHandlers = (1 to 10).map(_ => TestScalaEngine.getTestHandler)
  val cluster = TestScalaEngine.getTestCluster()
  
  implicit def toOption[A](a: A): Option[A] = Option(a)

  val storageServers =  cluster.getAvailableServers

  def createNS(name : String, repFactor : Int, readQuorum : Double, writeQuorum : Double) : SpecificNamespace[IntRec, IntRec] = {
    require(repFactor <= 10)
    val namespace = cluster.createNamespace[IntRec, IntRec](name, List((None,storageServers.slice(0, repFactor) )))
    namespace.setReadWriteQuorum(readQuorum, writeQuorum)
    return namespace
  }

  def getPartitions(storageServer : StorageService) : List[PartitionService] = {
    storageServer !? GetPartitionsRequest() match {
      case GetPartitionsResponse(partitions) => partitions
      case _ => throw new RuntimeException("Unknown message")
    }
  }

  "A Quorum Protocl" should {

    "respond after the read quorum" in {

      val ns = createNS("quorum3:2:2_read",3, 0.6, 0.6)
      val blockedPartitions = getPartitions(storageServers.head)
      messageHandler.blockReceivers(blockedPartitions)
      ns.put(IntRec(1), IntRec(2))
      messageHandler.unblockReceivers(blockedPartitions)
      ns.get(IntRec(1)).get.f1 should equal (2)
    }

    "respond after the write quorum" in {
      val ns = createNS("quorum3:2:2_write",3, 0.6, 0.6)
      val blockedPartitions = getPartitions(storageServers.head)
      ns.put(IntRec(1), IntRec(2))
      messageHandler.blockReceivers(blockedPartitions)
      ns.get(IntRec(1)).get.f1 should equal (2)
      messageHandler.unblockReceivers(blockedPartitions)
    }

    "read repair on get" in {
      val ns = createNS("quorum3:2:2_repair",3, 0.6, 0.6)
      val blockedPartitions = getPartitions(storageServers.head)
      ns.put(IntRec(1), IntRec(1))
      ns.get(IntRec(1)).get.f1  should equal(1)
      messageHandler.blockReceivers(blockedPartitions)
      ns.put(IntRec(1), IntRec(2))
      messageHandler.unblockReceivers(blockedPartitions)
      var values = ns.getAllVersions(IntRec(1)).map(_.get.f1)

      values should contain(1)
      values should contain(2)
      ns.get(IntRec(1)) //should trigger read repair
      Thread.sleep(1000)      
      values = ns.getAllVersions(IntRec(1)).map(_.get.f1)
      values should have length (3)
      values should (contain (2) and not contain (1))
    }

    "read repair with several servers" in {
      val ns = createNS("quorum10:6:6_repair",10, 0.51, 0.51)
      val blockedPartitions = storageServers.slice(0, 4).flatMap(getPartitions(_))

      ns.put(IntRec(1), IntRec(1))
      ns.get(IntRec(1)).get.f1  should equal(1)
      messageHandler.blockReceivers(blockedPartitions)
      ns.put(IntRec(1), IntRec(2))
      messageHandler.unblockReceivers(blockedPartitions)
      var values = ns.getAllVersions(IntRec(1)).map(_.get.f1)
      values should contain(1)
      values should contain(2)
      ns.get(IntRec(1)) //should trigger read repair
      Thread.sleep(1000)
      values = ns.getAllVersions(IntRec(1)).map(_.get.f1)
      values should have length (10)
      values should (contain (2) and not contain (1))

    }
    
    "read repair range requests" in {
      val ns = createNS("quorum3:2:2_range",3, 0.51, 0.51)
      val blockedPartitions = getPartitions(storageServers.head)
      (1 to 50).foreach(i => ns.put(IntRec(i),IntRec(1)))
      messageHandler.blockReceivers(blockedPartitions)
      (1 to 50).foreach(i => ns.put(IntRec(i),IntRec(2)))
      messageHandler.unblockReceivers(blockedPartitions)
      ns.getRange(None, None)
      val allVersions = (1 to 50).flatMap(i => ns.getAllVersions(IntRec(i)).map(_.get.f1))
      allVersions should (contain (2) and not contain (1))
    }

    "tolerate dead servers" is (pending)

    "tolerate message delays" is (pending)

    "increase the quorum when adding a new server" is (pending)

    "decrease the quorum when deleting a partition" is (pending)



  }

}

