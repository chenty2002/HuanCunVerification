import mill._
import scalalib._

trait HuanCunVerificationModule extends ScalaModule {

  def rocketModule: ScalaModule

  def huancunModule: ScalaModule

  override def moduleDeps = super.moduleDeps ++ Seq(rocketModule, huancunModule)
}
