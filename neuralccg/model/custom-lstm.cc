#include "custom-lstm.h"

using namespace cnn;

namespace neuralccg {

enum { X2I, H2I, C2I, BI, X2O, H2O, C2O, BO, X2C, H2C, BC };

// Equivalent to https://github.com/clab/cnn/blob/master/cnn/lstm.cc except we are saving the
// expressions of the input and forget gates.
Expression CustomLSTMBuilder::add_input_impl(int prev, const Expression& x) {
  h.push_back(std::vector<Expression>(layers));
  c.push_back(std::vector<Expression>(layers));
  std::vector<Expression>& ht = h.back();
  std::vector<Expression>& ct = c.back();
  Expression in = x;
  for (unsigned i = 0; i < layers; ++i) {
    const std::vector<Expression>& vars = param_vars[i];
    Expression i_h_tm1, i_c_tm1;
    bool has_prev_state = (prev >= 0 || has_initial_state);
    if (prev < 0) {
      if (has_initial_state) {
        // initial value for h and c at timestep 0 in layer i
        // defaults to zero matrix input if not set in add_parameter_edges
        i_h_tm1 = h0[i];
        i_c_tm1 = c0[i];
      }
    } else {  // t > 0
      i_h_tm1 = h[prev][i];
      i_c_tm1 = c[prev][i];
    }
    // apply dropout according to http://arxiv.org/pdf/1409.2329v5.pdf
    if (dropout_rate) in = dropout(in, dropout_rate);
    // input
    Expression i_ait;
    if (has_prev_state)
      i_ait = affine_transform({vars[BI], vars[X2I], in, vars[H2I], i_h_tm1, vars[C2I], i_c_tm1});
    else
      i_ait = affine_transform({vars[BI], vars[X2I], in});
    Expression i_it = logistic(i_ait);
    // forget
    Expression i_ft = 1.f - i_it;

    // Save the gate expressions.
    input_gates.push_back(i_it);
    forget_gates.push_back(i_ft);

    // write memory cell
    Expression i_awt;
    if (has_prev_state)
      i_awt = affine_transform({vars[BC], vars[X2C], in, vars[H2C], i_h_tm1});
    else
      i_awt = affine_transform({vars[BC], vars[X2C], in});
    Expression i_wt = tanh(i_awt);
    // output
    if (has_prev_state) {
      Expression i_nwt = cwise_multiply(i_it,i_wt);
      Expression i_crt = cwise_multiply(i_ft,i_c_tm1);
      ct[i] = i_crt + i_nwt;
    } else {
      ct[i] = cwise_multiply(i_it,i_wt);
    }

    Expression i_aot;
    if (has_prev_state)
      i_aot = affine_transform({vars[BO], vars[X2O], in, vars[H2O], i_h_tm1, vars[C2O], ct[i]});
    else
      i_aot = affine_transform({vars[BO], vars[X2O], in, vars[C2O], ct[i]});
    Expression i_ot = logistic(i_aot);
    Expression ph_t = tanh(ct[i]);
    in = ht[i] = cwise_multiply(i_ot,ph_t);
  }
  if (dropout_rate) return dropout(ht.back(), dropout_rate);
  else return ht.back();
};

void CustomLSTMBuilder::start_new_sequence_impl(const std::vector<Expression>& hinit) {
  LSTMBuilder::start_new_sequence_impl(hinit);
  input_gates.clear();
  forget_gates.clear();
}

}