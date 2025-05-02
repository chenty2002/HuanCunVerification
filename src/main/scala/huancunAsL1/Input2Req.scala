package huancunAsL1

import chisel3._
import huancun.prefetch._
import chipsalliance.rocketchip.config.Parameters

case class InputAsPrefectchParam() extends PrefetchParameters {
  override val hasPrefetchBit: Boolean = true
  override val inflightEntries: Int = 16
}

class Input2Req(implicit p: Parameters) extends Prefetcher {
  val io_inputAddr = IO(Input(UInt(fullAddressBits.W)))
  val io_inputNeedT = IO(Input(new Bool()))

  println("--------------------------------")
  println(" Modify Prefetcher as Input2Req ")
  println("--------------------------------")

  io.req.valid := true.B
  io.req.bits.tag := parseFullAddress(io_inputAddr)._1
  io.req.bits.set := parseFullAddress(io_inputAddr)._2
  io.req.bits.needT := io_inputNeedT
  io.req.bits.source := 0.U
  io.req.bits.isBOP := false.B
  // io.req.bits.pfSource := PfSource.NoWhere.id.U

  // train, resp, tlb_req are not used
  io.train.ready := true.B
  io.resp.ready := true.B
}