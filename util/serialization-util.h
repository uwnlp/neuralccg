#ifndef NEURALCCG_UTIL_SERIALIZATION_UTIL_H_
#define NEURALCCG_UTIL_SERIALIZATION_UTIL_H_

#include "cnn/cnn.h"
#include "neuralccg/proto/tensor.pb.h"

using namespace cnn;

namespace neuralccg {

class SerializationUtil {
 public:
  static void AsDimensionsProto(const Dim &dimensions, DimensionsProto *proto) {
    for (unsigned i = 0; i < dimensions.nd; ++i) {
      proto->add_dimension(dimensions.d[i]);
    }
  }

  static void FromDimensionsProto(const DimensionsProto &proto, Dim *dimensions) {
    dimensions->nd = proto.dimension_size();
    for (int i = 0; i < proto.dimension_size(); ++i) {
      dimensions->d[i] = proto.dimension(i);
    }
  }

  static void AsTensorProto(const Tensor &tensor, TensorProto *proto) {
    AsDimensionsProto(tensor.d, proto->mutable_dimensions());
    for (unsigned i = 0; i < tensor.d.size(); ++i) {
      proto->add_value(tensor.v[i]);
    }
  }

  // Assumes that the mutable tensor is preallocated to the right size.
  static void FromTensorProto(const TensorProto &proto, Tensor *tensor) {
    FromDimensionsProto(proto.dimensions(), &tensor->d);\
    assert(tensor->d.size() == static_cast<unsigned>(proto.value_size()));
    for (int i = 0; i < proto.value_size(); ++i) {
      tensor->v[i] = proto.value(i);
    }
  }

  static void AsParametersProto(const Parameters &parameters, ParametersProto *proto) {
    AsDimensionsProto(parameters.dim, proto->mutable_dimensions());
    AsTensorProto(parameters.values, proto->add_value());
  }

  static void AsParametersProto(const LookupParameters &parameters, ParametersProto *proto) {
    AsDimensionsProto(parameters.dim, proto->mutable_dimensions());
    for (const Tensor &value : parameters.values) {
      AsTensorProto(value, proto->add_value());
    }
  }

  static void FromParametersProto(const ParametersProto &proto, Parameters *parameters) {
    FromDimensionsProto(proto.dimensions(), &parameters->dim);
    assert(proto.value_size() == 1);
    FromTensorProto(proto.value(0), &parameters->values);
  }

  static void FromParametersProto(const ParametersProto &proto, LookupParameters *parameters) {
    FromDimensionsProto(proto.dimensions(), &parameters->dim);
    for (int i = 0; i < proto.value_size(); ++i) {
      FromTensorProto(proto.value(i), &parameters->values[i]);
    }
  }

  static void AsModelProto(const Model &model, ModelProto *proto) {
    for (Parameters *parameters : model.parameters_list()) {
      AsParametersProto(*parameters, proto->add_parameters());
    }
    for (LookupParameters *parameters : model.lookup_parameters_list()) {
      AsParametersProto(*parameters, proto->add_lookup_parameters());
    }
  }

  static void FromModelProto(const ModelProto &proto, Model *model) {
    assert(static_cast<int>(model->parameters_list().size()) == proto.parameters_size());
    for (int i = 0; i < proto.parameters_size(); ++i) {
      Dim dimensions;
      FromDimensionsProto(proto.parameters(i).dimensions(), &dimensions);
      assert(dimensions == model->parameters_list()[i]->dim);
      FromParametersProto(proto.parameters(i), model->parameters_list()[i]);
    }
    assert(static_cast<int>(model->lookup_parameters_list().size()) == proto.lookup_parameters_size());
    for (int i = 0; i < proto.lookup_parameters_size(); ++i) {
      Dim dimensions;
      FromDimensionsProto(proto.lookup_parameters(i).dimensions(), &dimensions);
      assert(dimensions == model->lookup_parameters_list()[i]->dim);
      FromParametersProto(proto.lookup_parameters(i), model->lookup_parameters_list()[i]);
    }
  }
};

}

#endif
