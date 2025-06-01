package huancunVerification

import chisel3._
import chisel3.stage.ChiselStage
import chisel3.util._
import chisel3.util.experimental.BoringUtils
import chiselFv._
import huancun._
import huancunAsL1._
import freechips.rocketchip.diplomacy._
import freechips.rocketchip.tilelink._
import chipsalliance.rocketchip.config._


class VerifyTop()(implicit p: Parameters) extends LazyModule {

  /* L1D   L1D
   *  |     |
   * L2    L2
   *  \    /
   *    L3
   */

  override lazy val desiredName: String = "VerifyTop"
  val delayFactor = 0.2
  val cacheParams = p(HCCacheParamsKey)

  val nrL2 = 2

  def createClientNode(name: String, sources: Int) = {
    val masterNode = TLClientNode(Seq(
      TLMasterPortParameters.v2(
        masters = Seq(
          TLMasterParameters.v1(
            name = name,
            sourceId = IdRange(0, sources),
            supportsProbe = TransferSizes(cacheParams.blockBytes)
          )
        ),
        channelBytes = TLChannelBeatBytes(cacheParams.blockBytes),
        minLatency = 1,
        echoFields = Seq(DirtyField()),
        requestFields = Seq(PrefetchField(), PreferCacheField(), DirtyField(), AliasField(2)),
        responseKeys = cacheParams.respKey
      )
    ))
    masterNode
  }
  val l0_nodes = (0 until nrL2).map(i => createClientNode(s"L0_$i", 32))

  val huancunAsL1 = (0 until nrL2).map(i => LazyModule(new HuanCunAsL1()(new Config((_, _, _) => {
    case HCCacheParamsKey => HCCacheParameters(
      name = s"L1",
      level = 1,
      ways = 2,
      sets = 2,
      mshrs = 4,
      blockBytes = 2,
      channelBytes = TLChannelBeatBytes(1),
      inclusive = false,
      clientCaches = Seq(CacheParameters(sets = 2, ways = 4, blockGranularity = 1, name = "L1")),
      prefetch = Some(InputAsPrefectchParam()),
      reqField = Seq(PreferCacheField()),
      echoField = Seq(DirtyField())
    )
  }))))
  val l1d_nodes = huancunAsL1.map(_.node)

  val huancunAsL2 = (0 until nrL2).map(i => LazyModule(new HuanCun()(new Config((_, _, _) => {
    case HCCacheParamsKey => HCCacheParameters(
      name = s"L2",
      level = 2,
      ways = 2,
      sets = 4,
      mshrs = 4,
      blockBytes = 2,
      channelBytes = TLChannelBeatBytes(1),
      inclusive = false,
      clientCaches = Seq(CacheParameters(sets = 2, ways = 4, blockGranularity = 1, name = "L2")),
      prefetch = Some(huancun.prefetch.BOPParameters()),
      reqField = Seq(PreferCacheField()),
      echoField = Seq(DirtyField())
    )
  }))))
  val l2_nodes = huancunAsL2.map(_.node)

  val l3 = LazyModule(new HuanCun()(new Config((_, _, _) => {
    case HCCacheParamsKey => HCCacheParameters(
      name = "L3",
      level = 3,
      ways = 2,
      sets = 4,
      mshrs = 6,
      blockBytes = 2,
      channelBytes = TLChannelBeatBytes(1),
      inclusive = false,
      clientCaches = Seq(CacheParameters(sets = 2, ways = 4, blockGranularity = 1, name = "L3")),
      echoField = Seq(DirtyField()),
      simulation = true
    )
  })))

  val xbar = TLXbar()
  val ram = LazyModule(new TLRAM(AddressSet(0, 0x1fL), beatBytes = 1))

  l0_nodes.zip(l1d_nodes) map {
    case (l0, l1d) => l1d := l0
  }

  l1d_nodes.zip(l2_nodes) map {
    case (l1d, l2) => l2 := TLBuffer() := l1d
  }

  l2_nodes.foreach { l2 => 
      xbar := TLBuffer() := l2
  }

  ram.node :=
    TLXbar() :=*
      TLFragmenter(1, 2) :=*
      TLCacheCork() :=*
      TLDelayer(delayFactor) :=*
      l3.node :=* xbar

  lazy val module = new LazyModuleImp(this) with Formal {
    l1d_nodes.foreach { node =>
      val (l1_in, _) = node.in.head
      dontTouch(l1_in)
    }

    val verify_timer = RegInit(0.U(50.W))
    verify_timer := verify_timer + 1.U

    fvAssert(verify_timer < 1000.U)

    val io = IO(Vec(nrL2, new Bundle() {
      // Input signals for formal verification
      val inputAddr = Input(UInt(ram.node.in.head._2.bundle.addressBits.W))
      val inputNeedT = Input(Bool())
    }))

    huancunAsL1.zipWithIndex.foreach{
      case (node, i) =>
        node.module.io_inputAddr := io(i).inputAddr
        node.module.io_inputNeedT := io(i).inputNeedT
    }

    huancunAsL2.foreach { l2 =>
      l2.module.slices.head.ms.zipWithIndex.foreach {
        case (mshr, i) =>
          val MSHRStatus = WireDefault(false.B)
          BoringUtils.bore(mshr.io.status.valid, Seq(MSHRStatus))
          assertLivenessTimer(MSHRStatus, !MSHRStatus, 500)
      }
    }
  }
}

object VerifyTop extends App {
  val config = new Config((_, _, _) => {
    case HCCacheParamsKey => HCCacheParameters(
      inclusive = false,
      clientCaches = Seq(CacheParameters(sets = 32, ways = 8, blockGranularity = 5, name = "L2", aliasBitsOpt = Some(2))),
      echoField = Seq(DirtyField())
    )
  })
  val top = DisableMonitors(p => LazyModule(new VerifyTop()(p)))(config)

  (new ChiselStage).emitSystemVerilog(
    top.module,
    Array("--target-dir", "Verilog")
  )
}