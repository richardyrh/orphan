`define DATA_WIDTH 8

import "DPI-C" function void memtrace_init(
    input  string  filename
);

import "DPI-C" function void memtrace_tick
(
    output bit     trace_read_valid,
    input  bit     trace_read_ready,
    output byte    trace_read_bits
);

module SimMemTrace  (
    input              clock,
    input              reset,

    output                   trace_read_valid,
    input                    trace_read_ready,
    output [`DATA_WIDTH-1:0] trace_read_bits
);

    bit __in_valid;
    byte __in_bits;
    string __uartlog;
    int __uartno;

    initial begin
        $value$plusargs("uartlog=%s", __uartlog);
        memtrace_init(__uartlog);
    end

    reg __in_valid_reg;
    reg [`DATA_WIDTH-1:0] __in_bits_reg;

    assign trace_read_valid  = __in_valid_reg;
    assign trace_read_bits   = __in_bits_reg;

    // Evaluate the signals on the positive edge
    always @(posedge clock) begin
        if (reset) begin
            __in_valid = 0;

            __in_valid_reg <= 0;
            __in_bits_reg <= 0;
        end else begin
            memtrace_tick(
                __in_valid,
                trace_read_ready,
                __in_bits
            );

            __in_valid_reg  <= __in_valid;
            __in_bits_reg   <= __in_bits;
        end
    end

endmodule