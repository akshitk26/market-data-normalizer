#include "itch_parser.hpp"

#include <stdexcept>

namespace marketdata::native {

namespace {

std::uint16_t read_u16_be(std::span<const std::uint8_t> bytes, std::size_t offset) {
    return static_cast<std::uint16_t>(
            (static_cast<std::uint16_t>(bytes[offset]) << 8) | bytes[offset + 1]);
}

std::uint64_t read_u48_be(std::span<const std::uint8_t> bytes, std::size_t offset) {
    std::uint64_t value = 0;
    for (std::size_t index = 0; index < 6; ++index) {
        value = (value << 8) | bytes[offset + index];
    }
    return value;
}

}  // namespace

ParsedItchMessage parse_itch_header(std::span<const std::uint8_t> bytes) {
    if (bytes.size() < 11) {
        throw std::invalid_argument("ITCH message header is truncated");
    }

    ParsedItchMessage parsed{};
    parsed.message_type = static_cast<char>(bytes[0]);
    // ITCH's common header is: type, stock locate, tracking number, 6-byte timestamp.
    parsed.sequence_number = read_u16_be(bytes, 3);
    parsed.timestamp_ns = read_u48_be(bytes, 5);
    return parsed;
}

}  // namespace marketdata::native
