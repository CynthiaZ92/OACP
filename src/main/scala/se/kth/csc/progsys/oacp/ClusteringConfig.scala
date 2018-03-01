package se.kth.csc.progsys.oacp.clustering

import com.typesafe.config.ConfigFactory

object ClusteringConfig {
  private val config = ConfigFactory.load()

  val clusterName = config.getString("clustering.cluster.name")

  val port = config.getString("clustering.port")

}
