#pragma once

#include <cstdint>
#include <span>

namespace marketdata::native {

struct ParsedItchMessage {
    char message_type;
    std::uint64_t sequence_number;
    std::uint64_t timestamp_ns;
};

ParsedItchMessage parse_itch_header(std::span<const std::uint8_t> bytes);

}  // namespace marketdata::native
