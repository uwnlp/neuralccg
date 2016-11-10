#ifndef NEURALCCG_MODEL_CUSTOM_LSTM_H
#define NEURALCCG_MODEL_CUSTOM_LSTM_H

#include "cnn/lstm.h"

using namespace cnn;

namespace neuralccg {

struct CustomLSTMBuilder : public LSTMBuilder {
  CustomLSTMBuilder() = default;
  explicit CustomLSTMBuilder(unsigned layers,
                             unsigned input_dim,
                             unsigned hidden_dim,
                             Model* model) : LSTMBuilder(layers, input_dim, hidden_dim, model) {}
  std::vector<Expression> input_gates, forget_gates;
 protected:
  Expression add_input_impl(int prev, const Expression& x) override;
  void start_new_sequence_impl(const std::vector<Expression>& hinit) override;
};

}

#endif
