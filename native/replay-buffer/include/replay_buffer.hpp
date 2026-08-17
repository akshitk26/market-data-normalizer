#pragma once

#include <cstddef>
#include <cstdint>
#include <vector>

namespace marketdata::native {

struct ReplayEntry {
    std::uint64_t sequence_number;
    std::vector<std::uint8_t> payload;
};

class ReplayBuffer {
public:
    explicit ReplayBuffer(std::size_t capacity);

    void append(ReplayEntry entry);
    [[nodiscard]] std::vector<ReplayEntry> replay(std::uint64_t from_sequence, std::uint64_t to_sequence) const;

private:
    std::vector<ReplayEntry> entries_;
    std::size_t write_index_{0};
    std::size_t size_{0};
};

}  // namespace marketdata::native
