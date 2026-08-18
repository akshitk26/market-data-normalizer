#include "replay_buffer.hpp"

#include <algorithm>
#include <stdexcept>

namespace marketdata::native {

ReplayBuffer::ReplayBuffer(std::size_t capacity) : entries_(capacity) {
    if (capacity == 0) {
        throw std::invalid_argument("capacity must be positive");
    }
}

void ReplayBuffer::append(ReplayEntry entry) {
    entries_[write_index_] = std::move(entry);
    write_index_ = (write_index_ + 1) % entries_.size();
    size_ = std::min(size_ + 1, entries_.size());
}

std::vector<ReplayEntry> ReplayBuffer::replay(std::uint64_t from_sequence, std::uint64_t to_sequence) const {
    std::vector<ReplayEntry> replayed;
    const std::size_t oldest_index = (write_index_ + entries_.size() - size_) % entries_.size();
    for (std::size_t i = 0; i < size_; ++i) {
        const ReplayEntry& entry = entries_[(oldest_index + i) % entries_.size()];
        if (entry.sequence_number >= from_sequence && entry.sequence_number <= to_sequence) {
            replayed.push_back(entry);
        }
    }

    std::sort(replayed.begin(), replayed.end(), [](const ReplayEntry& left, const ReplayEntry& right) {
        return left.sequence_number < right.sequence_number;
    });
    return replayed;
}

}  // namespace marketdata::native
