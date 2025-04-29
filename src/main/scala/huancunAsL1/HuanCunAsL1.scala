/** *************************************************************************************
  * Copyright (c) 2020-2021 Institute of Computing Technology, Chinese Academy of Sciences
  * Copyright (c) 2020-2021 Peng Cheng Laboratory
  *
  * XiangShan is licensed under Mulan PSL v2.
  * You can use this software according to the terms and conditions of the Mulan PSL v2.
  * You may obtain a copy of Mulan PSL v2 at:
  *          http://license.coscl.org.cn/MulanPSL2
  *
  * THIS SOFTWARE IS PROVIDED ON AN "AS IS" BASIS, WITHOUT WARRANTIES OF ANY KIND,
  * EITHER EXPRESS OR IMPLIED, INCLUDING BUT NOT LIMITED TO NON-INFRINGEMENT,
  * MERCHANTABILITY OR FIT FOR A PARTICULAR PURPOSE.
  *
  * See the Mulan PSL v2 for more details.
  * *************************************************************************************
  */

package huancunAsL1

import chisel3._
import huancun._
import freechips.rocketchip.diplomacy._
import chipsalliance.rocketchip.config.Parameters

class HuanCunAsL1(implicit p: Parameters) extends HuanCun {
  println(s"prefetchers: ${cacheParams.prefetch}")
  assert(cacheParams.prefetch.exists(_.isInstanceOf[InputAsPrefectchParam]))

  class HuanCunAsL1Imp(wrapper: LazyModule) extends HuanCunImp(wrapper) {
    override lazy val prefetcher = prefetchOpt.map(_ => Module(new Input2Req()(pftParams)))
    val fullAddrBits = node.in.head._2.bundle.addressBits

    // keep io_name same as before
    val io_inputAddr = IO(Input(UInt(fullAddrBits.W)))
    val io_inputNeedT = IO(Input(Bool()))

    prefetchOpt.foreach {
       _ =>
        prefetcher.get.io_inputAddr := io_inputAddr
        prefetcher.get.io_inputNeedT := io_inputNeedT
    }
  }

  override lazy val module = new HuanCunAsL1Imp(this)
}