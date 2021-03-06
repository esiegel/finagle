package com.twitter.finagle.redis.integration

import collection.mutable
import com.twitter.finagle.redis.Client
import com.twitter.finagle.redis.util.{CBToString, StringToChannelBuffer}
import java.util.UUID
import org.jboss.netty.buffer.ChannelBuffer
import org.specs.SpecificationWithJUnit


class BtreeClientSpec extends SpecificationWithJUnit {

  {
    "redis client" should {
      setSequential()

      var client: Client = null
      var dict: mutable.HashMap[String, mutable.HashMap[String, String]] = null

      "connect the client" in {
        // Until the Redis Server Btree changes are checked in and merged into master
        // The redis server needs to be started and shutdown externally
        // And the client connects it via the port 6379
        // After the changes become a part of the installed redis server
        // This will use RedisCluster to start and manage the external redis server
        val hostAddress = "127.0.0.1:6379"
        client = Client(hostAddress)
        dict = generateTestCases()
        require(client != null)
      }

      "test adding of outerkey, innerkey and value tuples using BADD command" in {
        testBadd(client, dict)
      }

      "test cardinality function for outerkey using BCARD command" in {
        testBcard(client, dict)
      }

      "test value for outerkey, innerkey pair using BGET command" in {
        testBget(client, dict)
      }

      "test BRANGE from start to end for outerkey" in {
        testBrange(client, dict)
      }

      "test BRANGE from a start key that exists to the end for outerkey" in {
        testBrangeInclusiveStart(client, dict)
      }

      "test BRANGE from start to end key that exists for outerkey" in {
        testBrangeInclusiveEnd(client, dict)
      }

      "test BRANGE from start key to end key where both exist for outerkey" in {
        testBrangeInclusiveStartEnd(client, dict)
      }

      "test BRANGE from start key that doesn't exist to end for outerkey" in {
        testBrangeExclusiveStart(client, dict)
      }

      "test BRANGE from start to end key that doesn't exist for outerkey" in {
        testBrangeExclusiveEnd(client, dict)
      }

      "test BRANGE from start key to end key where both don't exist for outerkey" in {
        testBrangeExclusiveStartEnd(client, dict)
      }

      "test removal of innerkey value pairs for outerkey using BREM command" in {
        testBrem(client, dict)
      }

      "test cardinality function for outerkey using BCARD command" in {
        testBcard(client, dict)
      }

      println("Closing client...")
      client.flushDB()
      client.release()
      println("Done!")
    }
  }

  def defaultTest(client: Client) {
    val key = "megatron"
    val value = "optimus"

    println("Setting " + key + "->" + value)
    client.set(StringToChannelBuffer(key), StringToChannelBuffer(value))
    println("Getting value for key " + key)
    val getResult = client.get(StringToChannelBuffer(key))()
    getResult match {
      case Some(n) => println("Got result: " + new String(n.array))
      case None => println("Didn't get the value!")
    }
  }

  def generateTestCases(): mutable.HashMap[String, mutable.HashMap[String, String]] = {
    val numSets = 100
    val setSize = 100

    val dict: mutable.HashMap[String, mutable.HashMap[String, String]] = new mutable.HashMap[String, mutable.HashMap[String, String]]

    for (i <- 0 until numSets) {
      val outerKey = UUID.randomUUID().toString
      val temp: mutable.HashMap[String, String] = new mutable.HashMap[String, String]
      for (j <- 0 until setSize) {
        val innerKey = UUID.randomUUID().toString
        val value = UUID.randomUUID().toString
        temp.put(innerKey, value)
      }
      dict.put(outerKey, temp)
    }

    dict
  }

  def testBadd(client: Client, dict: mutable.HashMap[String, mutable.HashMap[String, String]]) {
    for ((outerKey, inner) <- dict) {
      for ((innerKey, value) <- inner) {
        val target = client.bAdd(StringToChannelBuffer(outerKey), StringToChannelBuffer(innerKey), StringToChannelBuffer(value))
        require(target.get() == 1, "BADD failed for " + outerKey + " " + innerKey)
      }
    }

    println("Test BADD succeeded")
  }

  def testBcard(client: Client, dict: mutable.HashMap[String, mutable.HashMap[String, String]]) {
    for ((outerKey, inner) <- dict) {
      val target = client.bCard(StringToChannelBuffer(outerKey))
      require(inner.size == target.get,
        "BCARD failed for " + outerKey + " expected " + inner.size + " got " + target.get)
      }

      println("Test BCARD succeeded")
    }

  def testBget(client: Client, dict: mutable.HashMap[String, mutable.HashMap[String, String]]) {
    for ((outerKey, inner) <- dict) {
      for ((innerKey, value) <- inner) {
        val target = client.bGet(StringToChannelBuffer(outerKey), StringToChannelBuffer(innerKey))
        val targetVal = CBToString(target.get().get)
        require(value == targetVal, "BGET failed for " + outerKey + " expected " + value + " got " + targetVal)
      }
    }

    println("Test BGET succeeded")
  }

  def testBrem(client: Client, dict: mutable.HashMap[String, mutable.HashMap[String, String]]) {
    for ((outerKey, inner) <- dict) {
      for ((innerKey, value) <- inner) {
        val target = client.bRem(StringToChannelBuffer(outerKey), Seq(StringToChannelBuffer(innerKey)))
        require(target.get() == 1, "BREM failed for " + outerKey + " " + innerKey)
        inner.remove(innerKey)
      }
    }

    println("Test BREM succeeded")
  }

  def testBrange(client: Client, dict: mutable.HashMap[String, mutable.HashMap[String, String]]) {
    for ((outerKey, inner) <- dict) {
      val innerKeys = inner.toList.sortBy(_._1)
      val target = client.bRange(StringToChannelBuffer(outerKey), None, None).get()
      validate(outerKey, innerKeys, target)
    }

    println("Test BRANGE succeeded")
  }

  def testBrangeInclusiveStart(client: Client, dict: mutable.HashMap[String, mutable.HashMap[String, String]]) {
    val rand = new scala.util.Random()
    for ((outerKey, inner) <- dict) {
      var innerKeys = inner.toList.sortBy(_._1)
      val start = rand.nextInt(innerKeys.size)
      innerKeys = innerKeys.drop(start)
      val target = client.bRange(StringToChannelBuffer(outerKey), Option(StringToChannelBuffer(innerKeys.head._1)), None).get()
      validate(outerKey, innerKeys, target)
    }

    println("Test BRANGE Inclusive Start succeeded")
  }

  def testBrangeInclusiveEnd(client: Client, dict: mutable.HashMap[String, mutable.HashMap[String, String]]) {
    val rand = new scala.util.Random()
    for ((outerKey, inner) <- dict) {
      var innerKeys = inner.toList.sortBy(_._1)
      val end = rand.nextInt(innerKeys.size)
      innerKeys = innerKeys.dropRight(end)
      val target = client.bRange(StringToChannelBuffer(outerKey), None, Option(StringToChannelBuffer(innerKeys.last._1))).get()
      validate(outerKey, innerKeys, target)
    }

    println("Test BRANGE Inclusive End succeeded")
  }

  def testBrangeInclusiveStartEnd(client: Client, dict: mutable.HashMap[String, mutable.HashMap[String, String]]) {
    val rand = new scala.util.Random()
    for ((outerKey, inner) <- dict) {
      var innerKeys = inner.toList.sortBy(_._1)
      val start = rand.nextInt(innerKeys.size)
      val end = rand.nextInt(innerKeys.size)
      val target = client.bRange(
        StringToChannelBuffer(outerKey),
        Option(StringToChannelBuffer(innerKeys(start)._1)),
        Option(StringToChannelBuffer(innerKeys(end)._1)))

      if (start > end) {
        require(target.isThrow, "BRANGE failed for " + outerKey + " return should be a throw")
      }
      else {
        innerKeys = innerKeys.slice(start, end + 1)
        validate(outerKey, innerKeys, target.get())
      }
    }

    println("Test BRANGE Inclusive Start End succeeded")
  }

  def testBrangeExclusiveStart(client: Client, dict: mutable.HashMap[String, mutable.HashMap[String, String]]) {
    for ((outerKey, inner) <- dict) {
      var innerKeys = inner.toList.sortBy(_._1)
      val start = UUID.randomUUID().toString
      innerKeys = innerKeys.filter(p => (start <= p._1))
      val target = client.bRange(StringToChannelBuffer(outerKey), Option(StringToChannelBuffer(start)), None).get()
      validate(outerKey, innerKeys, target)
    }

    println("Test BRANGE Exclusive Start succeeded")
  }

  def testBrangeExclusiveEnd(client: Client, dict: mutable.HashMap[String, mutable.HashMap[String, String]]) {
    for ((outerKey, inner) <- dict) {
      var innerKeys = inner.toList.sortBy(_._1)
      val end = UUID.randomUUID().toString
      innerKeys = innerKeys.filter(p => (p._1 <= end))
      val target = client.bRange(StringToChannelBuffer(outerKey), None, Option(StringToChannelBuffer(end))).get()
      validate(outerKey, innerKeys, target)
    }

    println("Test BRANGE Exclusive End succeeded")
  }

  def testBrangeExclusiveStartEnd(client: Client, dict: mutable.HashMap[String, mutable.HashMap[String, String]]) {
    for ((outerKey, inner) <- dict) {
      var innerKeys = inner.toList.sortBy(_._1)
      val start = UUID.randomUUID().toString
      val end = UUID.randomUUID().toString
      innerKeys = innerKeys.filter(p => (start <= p._1 && p._1 <= end))
      val target = client.bRange(
        StringToChannelBuffer(outerKey),
        Option(StringToChannelBuffer(start)),
        Option(StringToChannelBuffer(end)))

      if (start > end) {
        require(target.isThrow, "BRANGE failed for " + outerKey + " return should be a throw")
      }
      else {
        validate(outerKey, innerKeys, target.get())
      }
    }

    println("Test BRANGE Exclusive Start End succeeded")
  }

  def validate(outerKey: String, exp: List[(String, String)], got: Seq[(ChannelBuffer, ChannelBuffer)]) {
    require(got.size == exp.size,
      "BRANGE failed for " + outerKey + " expected size " + exp.size + " got size " + got.size)

    for (i <- 0 until exp.size) {
      val expKey = exp(i)._1
      val gotKey = CBToString(got(i)._1)
      val expVal = exp(i)._2
      val gotVal = CBToString(got(i)._2)
      require(exp(i)._1 == CBToString(got(i)._1),
        "Key mismatch for outerKey " + outerKey + " expected " + expKey + "got " + gotKey)
      require(exp(i)._2 == CBToString(got(i)._2),
        "Value mismatch for outerKey " + outerKey + " expected " + expVal + "got " + gotVal)
    }
  }
}
