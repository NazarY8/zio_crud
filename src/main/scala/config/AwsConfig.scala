package config

import zio.Config
import zio.config.magnolia.DeriveConfig.deriveConfig


case class AwsConfig(region: String, accessKeyId: String, secretAccessKey: String)

object AwsConfig {
  // implicit val needed for Main Runner configLayer, without implicit will have to transfer config explicitly
  implicit val config: Config[AwsConfig] = deriveConfig[AwsConfig].nested("aws")
}