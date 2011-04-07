package edu.berkeley.cs
package scads
package piql
package tpcw

import comm._
import piql._
import perf._
import storage._
import avro.runtime._
import avro.marker._

import deploylib._
import deploylib.mesos._

case class TpcwLoaderTask(var numServers: Int,
                          var numLoaders: Int,
                          var numEBs: Double,
                          var numItems: Int,
                          var replicationFactor: Int = 1) extends DataLoadingTask with AvroRecord {
  var clusterAddress: String = _
  
  def run() = {
    val coordination = clusterRoot.getOrCreate("coordination/loaders")
    val cluster = new ExperimentalScadsCluster(clusterRoot)
    val tpcwClient = new TpcwClient(cluster, new SimpleExecutor)
    val loader = new TpcwLoader(tpcwClient,
      numClients = numLoaders,
      numEBs = numEBs,
      numItems = numItems)
    val clientId = coordination.registerAndAwait("clientStart", numLoaders)
    if (clientId == 0) {
      logger.info("Awaiting scads cluster startup")
      cluster.blockUntilReady(numServers)
      loader.createNamespaces
      import tpcwClient._
      List(addresses,
           authors,
           xacts,
           countries,
           items,
           orderLines,
           orders,
           shoppingCartItems) foreach { ns => ns.setReadWriteQuorum(0.33, 0.67) }
    }

    coordination.registerAndAwait("startBulkLoad", numLoaders)
    logger.info("Begining bulk loading of data")
    val data = loader.getData(clientId)
    data.load()

    logger.info("Bulk loading complete")
    coordination.registerAndAwait("loadingComplete", numLoaders)

    if(clientId == 0)
      clusterRoot.createChild("clusterReady", data=this.toBytes)

    System.exit(0)
  }

}
