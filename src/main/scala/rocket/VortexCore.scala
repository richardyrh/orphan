// See LICENSE.Berkeley for license details.
// See LICENSE.SiFive for license details.

package rocket

import chisel3._
import chisel3.util._
import chisel3.experimental._
import org.chipsalliance.cde.config.Parameters
import freechips.rocketchip.tile._
import tile.VortexTile

class VortexBundleA extends Bundle {
  val opcode = UInt(3.W) // FIXME: hardcoded
  val size = UInt(4.W) // FIXME: hardcoded
  val source = UInt(10.W) // FIXME: hardcoded
  val address = UInt(32.W) // FIXME: hardcoded
  val mask = UInt(4.W) // FIXME: hardcoded
  val data = UInt(32.W) // FIXME: hardcoded
}

class VortexBundleD extends Bundle {
  val opcode = UInt(3.W) // FIXME: hardcoded
  val size = UInt(4.W) // FIXME: hardcoded
  val source = UInt(10.W) // FIXME: hardcoded
  val data = UInt(32.W) // FIXME: hardcoded
}

class VortexBundle(tile: VortexTile)(implicit p: Parameters) extends CoreBundle {
  val clock = Input(Clock())
  val reset = Input(Reset())
  // val hartid = Input(UInt(hartIdLen.W))
  val reset_vector = Input(UInt(resetVectorLen.W))
  val interrupts = Input(new CoreInterrupts())
  
  // conditionally instantiate ports depending on whether we want to use VX_cache or not
  val imem = if (!tile.vortexParams.useVxCache) Some(Vec(1, new Bundle { // TODO: magic number
    val a = tile.imemNodes.head.out.head._1.a.cloneType
    val d = Flipped(tile.imemNodes.head.out.head._1.d.cloneType)
  })) else None
  val dmem = if (!tile.vortexParams.useVxCache) Some(Vec(tile.numLanes, new Bundle {
    val a = Decoupled(new VortexBundleA())
    val d = Flipped(Decoupled(new VortexBundleD()))
  })) else None
  val mem = if (tile.vortexParams.useVxCache) Some(new Bundle { 
    val a = tile.memNode.out.head._1.a.cloneType
    val d = Flipped(tile.memNode.out.head._1.d.cloneType)
  }) else None

  // val fpu = Flipped(new FPUCoreIO())
  //val rocc = Flipped(new RoCCCoreIO(nTotalRoCCCSRs))
  //val trace = Output(new TraceBundle)
  //val bpwatch = Output(Vec(coreParams.nBreakpoints, new BPWatch(coreParams.retireWidth)))
  val cease = Output(Bool())
  val wfi = Output(Bool())
  val traceStall = Input(Bool())
}

class Vortex(tile: VortexTile)(implicit p: Parameters)
    extends BlackBox(
      // Each Vortex core gets tied-off hartId of 0, 1, 2, 3, ...
      // The actual MHARTID read by the program is different by warp, not core;
      // see VX_csr_data that implements the read logic for CSR_MHARTID/GWID.
      Map("CORE_ID" -> tile.tileParams.hartId)
    )
    with HasBlackBoxResource {
  // addResource("/vsrc/vortex/hw/unit_tests/generic_queue/testbench.v")
  // addResource("/vsrc/vortex/hw/unit_tests/VX_divide_tb.v")
  // addResource("/vsrc/vortex/hw/syn/synopsys/models/memory/cln28hpm/rf2_256x19_wm0/rf2_256x19_wm0_rtl.v")
  // addResource("/vsrc/vortex/hw/syn/synopsys/models/memory/cln28hpm/rf2_256x19_wm0/rf2_256x19_wm0.v")
  // addResource("/vsrc/vortex/hw/syn/synopsys/models/memory/cln28hpm/rf2_32x19_wm0/rf2_32x19_wm0.v")
  // addResource("/vsrc/vortex/hw/syn/synopsys/models/memory/cln28hpm/rf2_32x19_wm0/rf2_32x19_wm0_rtl.v")
  // addResource("/vsrc/vortex/hw/syn/synopsys/models/memory/cln28hpm/rf2_32x128_wm1/rf2_32x128_wm1_rtl.v")
  // addResource("/vsrc/vortex/hw/syn/synopsys/models/memory/cln28hpm/rf2_32x128_wm1/rf2_32x128_wm1.v")
  // addResource("/vsrc/vortex/hw/syn/synopsys/models/memory/cln28hpm/rf2_32x128_wm1/vsim/rf2_32x128_wm1_tb.v")
  // addResource("/vsrc/vortex/hw/syn/synopsys/models/memory/cln28hpm/rf2_256x128_wm1/rf2_256x128_wm1_rtl.v")
  // addResource("/vsrc/vortex/hw/syn/synopsys/models/memory/cln28hpm/rf2_256x128_wm1/rf2_256x128_wm1.v")
  // addResource("/vsrc/vortex/hw/syn/synopsys/models/memory/cln28hpm/rf2_128x128_wm1/rf2_128x128_wm1.v")
  // addResource("/vsrc/vortex/hw/syn/synopsys/models/memory/cln28hpm/rf2_128x128_wm1/rf2_128x128_wm1_rtl.v")
  // addResource("/vsrc/vortex/hw/syn/synopsys/models/memory/cln28hpc/rf2_32x128_wm1/rf2_32x128_wm1_rtl.v")
  // addResource("/vsrc/vortex/hw/syn/synopsys/models/memory/cln28hpc/rf2_32x128_wm1/rf2_32x128_wm1.v")
  // addResource("/vsrc/vortex/hw/syn/synopsys/models/memory/cln28hpc/rf2_32x128_wm1/vsim/rf2_32x128_wm1_tb.v")
  // addResource("/vsrc/vortex/hw/syn/modelsim/vortex_tb.v")
  addResource("/vsrc/vortex/hw/rtl/VX_dispatch.sv")
  addResource("/vsrc/vortex/hw/rtl/VX_issue.sv")

  addResource("/vsrc/vortex/hw/rtl/cache/VX_cache_define.vh")
  
  addResource("/vsrc/vortex/hw/rtl/VX_warp_sched.sv")
  // addResource("/vsrc/vortex/hw/rtl/Vortex.sv")
  addResource("/vsrc/vortex/hw/rtl/tex_unit/VX_tex_sat.sv")
  addResource("/vsrc/vortex/hw/rtl/tex_unit/VX_tex_stride.sv")
  addResource("/vsrc/vortex/hw/rtl/tex_unit/VX_tex_lerp.sv")
  addResource("/vsrc/vortex/hw/rtl/tex_unit/VX_tex_addr.sv")
  addResource("/vsrc/vortex/hw/rtl/tex_unit/VX_tex_mem.sv")
  addResource("/vsrc/vortex/hw/rtl/tex_unit/VX_tex_format.sv")
  addResource("/vsrc/vortex/hw/rtl/tex_unit/VX_tex_sampler.sv")
  addResource("/vsrc/vortex/hw/rtl/tex_unit/VX_tex_unit.sv")
  addResource("/vsrc/vortex/hw/rtl/tex_unit/VX_tex_define.vh")
  addResource("/vsrc/vortex/hw/rtl/tex_unit/VX_tex_wrap.sv")
  addResource("/vsrc/vortex/hw/rtl/VX_scope.vh")
  addResource("/vsrc/vortex/hw/rtl/VX_fpu_unit.sv")
  addResource("/vsrc/vortex/hw/rtl/VX_scoreboard.sv")
  addResource("/vsrc/vortex/hw/rtl/VX_writeback.sv")
  addResource("/vsrc/vortex/hw/rtl/VX_muldiv.sv")
  addResource("/vsrc/vortex/hw/rtl/VX_decode.sv")
  addResource("/vsrc/vortex/hw/rtl/VX_ibuffer.sv")
  // addResource("/vsrc/vortex/hw/rtl/VX_cluster.sv")
  addResource("/vsrc/vortex/hw/rtl/VX_icache_stage.sv")
  addResource("/vsrc/vortex/hw/rtl/VX_gpu_unit.sv")
  addResource("/vsrc/vortex/hw/rtl/VX_trace_instr.vh")
  addResource("/vsrc/vortex/hw/rtl/VX_gpu_types.vh")
  addResource("/vsrc/vortex/hw/rtl/VX_config.vh")
  // unused addResource("/vsrc/vortex/hw/rtl/libs/VX_mux.sv")
  addResource("/vsrc/vortex/hw/rtl/libs/VX_lzc.sv")
  addResource("/vsrc/vortex/hw/rtl/libs/VX_fifo_queue.sv")
  addResource("/vsrc/vortex/hw/rtl/libs/VX_scan.sv")
  addResource("/vsrc/vortex/hw/rtl/libs/VX_find_first.sv")
  addResource("/vsrc/vortex/hw/rtl/libs/VX_multiplier.sv")
  addResource("/vsrc/vortex/hw/rtl/libs/VX_bits_remove.sv")
  addResource("/vsrc/vortex/hw/rtl/libs/VX_pipe_register.sv")
  // unused addResource("/vsrc/vortex/hw/rtl/libs/VX_onehot_mux.sv")
  addResource("/vsrc/vortex/hw/rtl/libs/VX_priority_encoder.sv")
  addResource("/vsrc/vortex/hw/rtl/libs/VX_reset_relay.sv")
  addResource("/vsrc/vortex/hw/rtl/libs/VX_popcount.sv")
  addResource("/vsrc/vortex/hw/rtl/libs/VX_bits_insert.sv")
  addResource("/vsrc/vortex/hw/rtl/libs/VX_skid_buffer.sv")
  addResource("/vsrc/vortex/hw/rtl/libs/VX_fixed_arbiter.sv")
  addResource("/vsrc/vortex/hw/rtl/libs/VX_shift_register.sv")
  addResource("/vsrc/vortex/hw/rtl/libs/VX_index_buffer.sv")
  addResource("/vsrc/vortex/hw/rtl/libs/VX_onehot_encoder.sv")
  addResource("/vsrc/vortex/hw/rtl/libs/VX_matrix_arbiter.sv")
  // unused addResource("/vsrc/vortex/hw/rtl/libs/VX_divider.sv")
  addResource("/vsrc/vortex/hw/rtl/libs/VX_dp_ram.sv")
  addResource("/vsrc/vortex/hw/rtl/libs/VX_axi_adapter.sv")
  addResource("/vsrc/vortex/hw/rtl/libs/VX_elastic_buffer.sv")
  addResource("/vsrc/vortex/hw/rtl/libs/VX_rr_arbiter.sv")
  addResource("/vsrc/vortex/hw/rtl/libs/VX_stream_arbiter.sv")
  // unused addResource("/vsrc/vortex/hw/rtl/libs/VX_bypass_buffer.sv")
  addResource("/vsrc/vortex/hw/rtl/libs/VX_sp_ram.sv")
  addResource("/vsrc/vortex/hw/rtl/libs/VX_stream_demux.sv")
  
  // unused addResource("/vsrc/vortex/hw/rtl/libs/VX_index_queue.sv")
  addResource("/vsrc/vortex/hw/rtl/libs/VX_serial_div.sv")
  addResource("/vsrc/vortex/hw/rtl/libs/VX_fair_arbiter.sv")
  addResource("/vsrc/vortex/hw/rtl/VX_define.vh")
  addResource("/vsrc/vortex/hw/rtl/VX_csr_data.sv")
  addResource("/vsrc/vortex/hw/rtl/VX_cache_arb.sv")
  addResource("/vsrc/vortex/hw/rtl/VX_ipdom_stack.sv")
  addResource("/vsrc/vortex/hw/rtl/VX_gpr_stage.sv")
  addResource("/vsrc/vortex/hw/rtl/VX_execute.sv")
  addResource("/vsrc/vortex/hw/rtl/VX_fetch.sv")
  addResource("/vsrc/vortex/hw/rtl/VX_alu_unit.sv")
  addResource("/vsrc/vortex/hw/rtl/VX_platform.vh")
  addResource("/vsrc/vortex/hw/rtl/VX_commit.sv")
  
  addResource("/vsrc/vortex/hw/rtl/VX_pipeline.sv")
  addResource("/vsrc/vortex/hw/rtl/VX_lsu_unit.sv")
  addResource("/vsrc/vortex/hw/rtl/VX_csr_unit.sv")
  // addResource("/vsrc/vortex/hw/rtl/Vortex_axi.sv")
  // addResource("/vsrc/vortex/hw/rtl/fp_cores/VX_fp_div.sv")
  addResource("/vsrc/vortex/hw/VX_config.h")
  addResource("/vsrc/vortex/sim/common/rvfloats.h")
  addResource("/vsrc/vortex/sim/common/rvfloats.cpp")
  // addResource("/csrc/softfloat_archive.a")
  addResource("/csrc/softfloat/include/internals.h")
  addResource("/csrc/softfloat/include/primitives.h")
  addResource("/csrc/softfloat/include/primitiveTypes.h")
  addResource("/csrc/softfloat/include/softfloat.h")
  addResource("/csrc/softfloat/include/softfloat_types.h")
  addResource("/csrc/softfloat/RISCV/specialize.h")
  addResource("/vsrc/vortex/hw/dpi/float_dpi.cpp")
  addResource("/vsrc/vortex/hw/dpi/float_dpi.vh")
  addResource("/vsrc/vortex/hw/dpi/util_dpi.cpp")
  addResource("/vsrc/vortex/hw/dpi/util_dpi.vh")
  addResource("/vsrc/vortex/hw/rtl/fp_cores/VX_fpu_dpi.sv")
  // addResource("/vsrc/vortex/hw/rtl/fp_cores/VX_fp_rounding.sv")
  // addResource("/vsrc/vortex/hw/rtl/fp_cores/altera/stratix10/dspba_delay_ver.sv")
  // addResource("/vsrc/vortex/hw/rtl/fp_cores/altera/stratix10/acl_fsqrt.sv")
  // addResource("/vsrc/vortex/hw/rtl/fp_cores/altera/stratix10/acl_fdiv.sv")
  // addResource("/vsrc/vortex/hw/rtl/fp_cores/altera/stratix10/acl_fmadd.sv")
  // addResource("/vsrc/vortex/hw/rtl/fp_cores/altera/arria10/dspba_delay_ver.sv")
  // addResource("/vsrc/vortex/hw/rtl/fp_cores/altera/arria10/acl_fsqrt.sv")
  // addResource("/vsrc/vortex/hw/rtl/fp_cores/altera/arria10/acl_fdiv.sv")
  // addResource("/vsrc/vortex/hw/rtl/fp_cores/altera/arria10/acl_fmadd.sv")
  // addResource("/vsrc/vortex/hw/rtl/fp_cores/VX_fp_class.sv")
  // addResource("/vsrc/vortex/hw/rtl/fp_cores/VX_fpu_fpnew.sv")
  // addResource("/vsrc/vortex/hw/rtl/fp_cores/VX_fp_cvt.sv")
  addResource("/vsrc/vortex/hw/rtl/fp_cores/VX_fpu_define.vh")
  // addResource("/vsrc/vortex/hw/rtl/fp_cores/VX_fp_fma.sv")
  // addResource("/vsrc/vortex/hw/rtl/fp_cores/VX_fp_ncomp.sv")
  // addResource("/vsrc/vortex/hw/rtl/fp_cores/VX_fpu_fpga.sv")
  addResource("/vsrc/vortex/hw/rtl/fp_cores/VX_fpu_types.vh")
  // addResource("/vsrc/vortex/hw/rtl/fp_cores/VX_fp_sqrt.sv")
  addResource("/vsrc/vortex/hw/rtl/interfaces/VX_icache_rsp_if.sv")
  addResource("/vsrc/vortex/hw/rtl/interfaces/VX_dcache_req_if.sv")
  addResource("/vsrc/vortex/hw/rtl/interfaces/VX_tex_csr_if.sv")
  addResource("/vsrc/vortex/hw/rtl/interfaces/VX_join_if.sv")
  addResource("/vsrc/vortex/hw/rtl/interfaces/VX_ifetch_req_if.sv")
  addResource("/vsrc/vortex/hw/rtl/interfaces/VX_perf_cache_if.sv")
  addResource("/vsrc/vortex/hw/rtl/interfaces/VX_perf_memsys_if.sv")
  addResource("/vsrc/vortex/hw/rtl/interfaces/VX_gpr_req_if.sv")
  addResource("/vsrc/vortex/hw/rtl/interfaces/VX_decode_if.sv")
  addResource("/vsrc/vortex/hw/rtl/interfaces/VX_writeback_if.sv")
  addResource("/vsrc/vortex/hw/rtl/interfaces/VX_gpu_req_if.sv")
  addResource("/vsrc/vortex/hw/rtl/interfaces/VX_perf_pipeline_if.sv")
  addResource("/vsrc/vortex/hw/rtl/interfaces/VX_gpr_rsp_if.sv")
  addResource("/vsrc/vortex/hw/rtl/interfaces/VX_cmt_to_csr_if.sv")
  addResource("/vsrc/vortex/hw/rtl/interfaces/VX_csr_to_alu_if.sv")
  addResource("/vsrc/vortex/hw/rtl/interfaces/VX_ifetch_rsp_if.sv")
  addResource("/vsrc/vortex/hw/rtl/interfaces/VX_alu_req_if.sv")
  addResource("/vsrc/vortex/hw/rtl/interfaces/VX_csr_req_if.sv")
  addResource("/vsrc/vortex/hw/rtl/interfaces/VX_ibuffer_if.sv")
  addResource("/vsrc/vortex/hw/rtl/interfaces/VX_branch_ctl_if.sv")
  addResource("/vsrc/vortex/hw/rtl/interfaces/VX_dcache_rsp_if.sv")
  addResource("/vsrc/vortex/hw/rtl/interfaces/VX_icache_req_if.sv")
  addResource("/vsrc/vortex/hw/rtl/interfaces/VX_lsu_req_if.sv")
  addResource("/vsrc/vortex/hw/rtl/interfaces/VX_wstall_if.sv")
  addResource("/vsrc/vortex/hw/rtl/interfaces/VX_mem_rsp_if.sv")
  addResource("/vsrc/vortex/hw/rtl/interfaces/VX_fpu_to_csr_if.sv")
  addResource("/vsrc/vortex/hw/rtl/interfaces/VX_commit_if.sv")
  addResource("/vsrc/vortex/hw/rtl/interfaces/VX_tex_req_if.sv")
  addResource("/vsrc/vortex/hw/rtl/interfaces/VX_warp_ctl_if.sv")
  addResource("/vsrc/vortex/hw/rtl/interfaces/VX_tex_rsp_if.sv")
  addResource("/vsrc/vortex/hw/rtl/interfaces/VX_fetch_to_csr_if.sv")
  addResource("/vsrc/vortex/hw/rtl/interfaces/VX_perf_tex_if.sv")
  addResource("/vsrc/vortex/hw/rtl/interfaces/VX_mem_req_if.sv")
  addResource("/vsrc/vortex/hw/rtl/interfaces/VX_fpu_req_if.sv")
  // addResource("/vsrc/vortex/hw/rtl/afu/vortex_afu.sv")
  // addResource("/vsrc/vortex/hw/rtl/afu/ccip_std_afu.sv")
  // addResource("/vsrc/vortex/hw/rtl/afu/vortex_afu.vh")
  // addResource("/vsrc/vortex/hw/rtl/afu/ccip/local_mem_cfg_pkg.sv")
  // addResource("/vsrc/vortex/hw/rtl/afu/ccip/ccip_if_pkg.sv")
  // addResource("/vsrc/vortex/hw/rtl/afu/ccip_interface_reg.sv")
  // addResource("/vsrc/vortex/hw/rtl/afu/VX_avs_wrapper.sv")
  // addResource("/vsrc/vortex/hw/rtl/afu/VX_to_mem.sv")
  // addResource("/vsrc/vortex/sim/vlsim/vortex_afu_shim.sv")
  if (tile.vortexParams.useVxCache) {
    addResource("/vsrc/vortex/hw/rtl/libs/VX_pending_size.sv")
    addResource("/vsrc/vortex/hw/rtl/cache/VX_shared_mem.sv")
    addResource("/vsrc/vortex/hw/rtl/cache/VX_core_rsp_merge.sv")
    addResource("/vsrc/vortex/hw/rtl/cache/VX_tag_access.sv")
    addResource("/vsrc/vortex/hw/rtl/cache/VX_core_req_bank_sel.sv")
    addResource("/vsrc/vortex/hw/rtl/cache/VX_bank.sv")
    addResource("/vsrc/vortex/hw/rtl/cache/VX_data_access.sv")
    addResource("/vsrc/vortex/hw/rtl/cache/VX_flush_ctrl.sv")
    addResource("/vsrc/vortex/hw/rtl/cache/VX_nc_bypass.sv")
    addResource("/vsrc/vortex/hw/rtl/cache/VX_miss_resrv.sv")
    addResource("/vsrc/vortex/hw/rtl/cache/VX_cache.sv")
    addResource("/vsrc/vortex/hw/rtl/VX_mem_arb.sv")
    addResource("/vsrc/vortex/hw/rtl/VX_smem_arb.sv")
    addResource("/vsrc/vortex/hw/rtl/VX_mem_unit.sv")
    addResource("/vsrc/vortex/hw/rtl/VX_core.sv")
    addResource("/vsrc/vortex/hw/rtl/VX_core_wrapper.sv")
  } else {
    addResource("/vsrc/vortex/hw/rtl/VX_pipeline_wrapper.sv")
  }

  val nTotalRoCCCSRs = 0
  val coreBundle = new VortexBundle(tile)
  val io = IO(coreBundle)
}
