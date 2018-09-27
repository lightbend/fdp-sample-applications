
# coding: utf-8

# In[39]:


from __future__ import division

import math
import pandas as pd
import datetime as dt
import ConfigParser

import matplotlib.pyplot as plt
from scipy import stats
import seaborn as sns
from pylab import rcParams
from sklearn.model_selection import train_test_split
from sklearn import preprocessing
from sklearn.metrics import mean_squared_error

import numpy as np
import time
from numpy import arange, sin, pi, random

from pyspark import SparkContext

# get_ipython().run_line_magic('matplotlib', 'inline')
# sns.set(style='whitegrid', palette='muted', font_scale=1.5)
# rcParams['figure.figsize'] = 14, 8
RANDOM_SEED = 42
LABELS = ["Normal", "Anomaly"]
lookback = 3


# In[40]:


from bigdl.nn.layer import *
from bigdl.nn.criterion import *
from bigdl.optim.optimizer import *
from bigdl.util import common
from bigdl.util.common import *

sc = SparkContext.getOrCreate(conf=create_spark_conf().setMaster("local[4]").set("spark.driver.memory","2g"))
init_engine()


# In[41]:


import os

## for running on cluster
data_file_path = os.getenv("DATA_FILE_NAME")
generation_complete_file_path = os.getenv("GENERATION_COMPLETE_FILE_NAME")
model_pb_file_path = os.getenv("MODEL_PB_FILE_NAME")
model_attrib_file_path = os.getenv("MODEL_ATTRIB_FILE_NAME")
hyperparams_file_path = os.getenv("HYPERPARAMS_FILE_NAME")

## for local runs possibly these will not be set in the environment
if not data_file_path:
    data_file_path = 'data/CPU_examples.csv'

if not generation_complete_file_path:
    generation_complete_file_path = '/tmp/data_preparation_complete.txt'
    
if not model_pb_file_path:
    model_pb_file_path = '/tmp/model.pb'
    
if not model_attrib_file_path:
    model_attrib_file_path = '/tmp/model-attributes.properties'

# local_model_file_path = '/tmp/model.bigdl'
# local_model_weights_file_path = '/tmp/model.bin'

if not hyperparams_file_path:
    hyperparams_file_path = '/tmp/hyperparams.properties'

print(data_file_path)
print(generation_complete_file_path)
print(model_pb_file_path)
print(model_attrib_file_path)
print(hyperparams_file_path)


# ## Read data from csv

# In[42]:


df = pd.read_csv(data_file_path)


# In[43]:


df.shape


# In[44]:


df.head()


# ## Basic sanity check of data and normalization

# In[45]:


# check for null data
df.isnull().values.any()

# Standard scaling : mean 0, stddev 1
scaler = preprocessing.StandardScaler()
df['CPU'] = scaler.fit_transform(df['CPU'].values.reshape(-1, 1))

scaler_mean = scaler.mean_
scaler_var = scaler.var_ ** 0.5

print(scaler_mean)  ## mean
print(scaler_var)   ## std dev

df.head()


# In[46]:


# let's explore the distribution of input data
count_classes = pd.value_counts(df['Class'], sort = False)
print(count_classes.count)
# count_classes.plot(kind = 'bar', rot=0)
# plt.title("Transaction class distribution")
# plt.xticks(range(2), LABELS)
# plt.xlabel("Class")
# plt.ylabel("Frequency");


# In[47]:


# Just checking the relative counts
anomaly = df[df.Class == 1]
normal = df[df.Class == 0]

anomaly.shape


# In[48]:


normal.shape


# In[49]:


anomaly.CPU.describe()


# In[50]:


normal.CPU.describe()


# ## Any correlation between time and CPU metrics ?

# In[51]:


# f, (ax1, ax2) = plt.subplots(2, 1, sharex=True)
# f.suptitle('Time vs CPU by class')

# ax1.scatter(anomaly.Time, anomaly.CPU)
# ax1.set_title('Anomaly')

# ax2.scatter(normal.Time, normal.CPU)
# ax2.set_title('Normal')

# plt.xlabel('Time (in Seconds)')
# plt.ylabel('CPU')
# plt.show()


# ## Feature Re-engineering

# In[52]:


def widenX(width, x) :
    slicestart = 0
    sliceend = width

    X_data = []
    newlength = x.size - width + 1
    for i in range(newlength):
        X_data.append(x[slicestart:sliceend])
        slicestart = slicestart + 1
        sliceend = sliceend + 1

    return np.reshape(X_data, newshape=(newlength, width))

def widenY(width, y):
    return y[width-1:]


# In[53]:


df.head()
anomaly = df[df.Class == 1]
anomaly.shape


# In[54]:


X_train = widenX(lookback, df['CPU'])
X_train.shape


# In[55]:


Y_train = widenY(lookback, df['Class'])
Y_train.shape


# In[56]:


class_0 = list(filter(lambda x: x == 0.0, Y_train)) 
class_1 = list(filter(lambda x: x == 1.0, Y_train)) 
print(len(class_0), len(class_1))


# ## Split into training and test set

# In[57]:


original_X_train_size = X_train.shape[0]
train_set_size = int(0.8 * original_X_train_size)
test_set_size = original_X_train_size - train_set_size

X_test = X_train[original_X_train_size - test_set_size : -1]
X_train = X_train[0 : train_set_size]

Y_test = Y_train[original_X_train_size - test_set_size : -1]
Y_train = Y_train[0 : train_set_size]

print(int(train_set_size))
print(int(test_set_size))
print(X_train.shape)
print(X_test.shape)


# In[58]:


X_train.shape


# In[59]:


X_test.shape


# In order to get data into BigDL engine we need to convert the numpy arrays into Spark RDD. Here's a helper function that does this. Note that we will use negative log likelihood for loss computation - hence based on the documentation of BigDL, the labels need to start from 1. Hence we add a 1 in the function below to all the labels.

# In[60]:


def get_rdd_from_ndarray(sc):
    rdd_X_train = sc.parallelize(X_train)
    rdd_Y_train = sc.parallelize(Y_train)
    rdd_X_test = sc.parallelize(X_test)
    rdd_Y_test = sc.parallelize(Y_test)

    rdd_train_sample = rdd_X_train.zip(rdd_Y_train).map(lambda labeledFeatures:
                                       common.Sample.from_ndarray(labeledFeatures[0], labeledFeatures[1]+1))
    rdd_test_sample = rdd_X_test.zip(rdd_Y_test).map(lambda labeledFeatures:
                                     common.Sample.from_ndarray(labeledFeatures[0], labeledFeatures[1]+1))
    return (rdd_train_sample, rdd_test_sample)

(train_data, test_data) = get_rdd_from_ndarray(sc)


# # Build the Model

# In[61]:


# create a graph model
def make_new_model(X_train):

    ## input layer with relu and dropout
    initial = Linear(X_train.shape[1], 16).set_name("input")()
    relu1 = ReLU()(initial)
    dropout1 = Dropout(0.3)(relu1)

    ## first hidden layer with relu and dropout
    hidden1 = Linear(16, 32)(dropout1)
    relu2 = ReLU()(hidden1)
    dropout2 = Dropout(0.4)(relu2)

    ## second hidden layer with relu and dropout
    hidden2 = Linear(32, 32)(dropout2)
    relu3 = ReLU()(hidden2)
    dropout3 = Dropout(0.4)(relu3)

    ## output layer with softmax(2) and dropout
    output = Linear(32, 2)(dropout3)
    softmax = SoftMax().set_name("output")(output)

    return Model([initial], [softmax])


# ## Fetch Hyperparameters

# We want to fetch hyperparameters from a file. Besides externalizing the hyperparameters, this also allows us to train multiple models by passing in different hyperparameters.

# In[62]:


# read hyperparameters from file, if exists
if (os.path.exists(hyperparams_file_path)):
    print("Got hyperparameter file ..")    
    config = ConfigParser.RawConfigParser()
    config.read(hyperparams_file_path)
    learning_rate = float(config.get('HyperparameterSection', 'learning_rate'))
    training_epochs = int(config.get('HyperparameterSection', 'training_epochs'))
    batch_size = int(config.get('HyperparameterSection', 'batch_size'))
else:
    learning_rate = 0.001
    training_epochs = 8
    batch_size = 256
    
print('learning rate', learning_rate)
print('training epochs', training_epochs)
print('batch size', batch_size)

# since we save the model every time, we need to hash the file name based on hyperparameter
# settings, so that the correct file name is picked up when we attempt to find and load an exiating model
local_model_file_path = '/tmp/model-' + str(learning_rate) + '-' + str(training_epochs) + '-' + str(batch_size) + '.bigdl'
local_model_weights_file_path = '/tmp/model-' + str(learning_rate) + '-' + str(training_epochs) + '-' + str(batch_size) + '.bin'


# In[63]:


print(local_model_file_path)
print(local_model_weights_file_path)


# ## Load Model for Incremental Learning

# We would like to do incremental learning. Hence if we find an existing BigDL model and the weights, then we load from that model and then continue training. Else we build the new model. The model and the weights file names are hashed based on hyperparameter settings.

# In[64]:


if (os.path.exists(local_model_file_path) and os.path.exists(local_model_weights_file_path)):
    print("Got existing model .. loading ..")
    model = Model.loadModel(local_model_file_path, local_model_weights_file_path) # load from local fs
else:
    model = make_new_model(X_train)


# ## Optimize and Train

# In[65]:


optimizer = Optimizer(
    model = model,
    training_rdd = train_data,
    criterion = ClassNLLCriterion(),
    optim_method = Adam(learningrate=learning_rate),
    end_trigger = MaxEpoch(training_epochs),
    batch_size = batch_size)

# Set the validation logic
optimizer.set_validation(
    batch_size = batch_size,
    val_rdd = test_data,
    trigger = EveryEpoch(),
    val_method = [Top1Accuracy()]
)

log_dir = 'mylogdir'

app_name='anomaly-cpu-' + dt.datetime.now().strftime("%Y%m%d-%H%M%S")
train_summary = TrainSummary(log_dir=log_dir, app_name=app_name)
train_summary.set_summary_trigger("Parameters", SeveralIteration(50))
val_summary = ValidationSummary(log_dir=log_dir, app_name=app_name)
optimizer.set_train_summary(train_summary)
optimizer.set_val_summary(val_summary)
print("saving logs to ", app_name)


# In[66]:


# get_ipython().run_cell_magic('time', '', '# Boot training process\ntrained_model = optimizer.optimize()\nprint("Optimization Done.")')
trained_model = optimizer.optimize()
print("Optimization Done.")


# In[67]:


# save BigDL model locally
model.saveModel(local_model_file_path, local_model_weights_file_path, True) # save to local fs

# model.save_tensorflow([("input", [1, 3])], "/tmp/model.pb")
model.save_tensorflow([("input", [1, 3])], model_pb_file_path)

loss = np.array(train_summary.read_scalar("Loss"))
top1 = np.array(val_summary.read_scalar("Top1Accuracy"))

# plt.figure(figsize = (12,12))
# plt.subplot(2,1,1)
# plt.plot(loss[:,0],loss[:,1],label='loss')
# plt.xlim(0,loss.shape[0]+10)
# plt.grid(True)
# plt.title("loss")
# plt.subplot(2,1,2)
# plt.plot(top1[:,0],top1[:,1],label='top1')
# plt.xlim(0,loss.shape[0]+10)
# plt.title("top1 accuracy")
# plt.grid(True)


# # Predict on test set

# In[68]:


def map_predict_label(l):
    return np.array(l).argmax()
def map_groundtruth_label(l):
    return int(l[0] - 1)


# In[69]:


predictions = trained_model.predict(test_data)


# In[70]:


# get_ipython().run_cell_magic('time', '', "predictions = trained_model.predict(test_data)\nprint('Ground Truth labels:')\nprint(', '.join(str(map_groundtruth_label(s.label.to_ndarray())) for s in test_data.take(50)))\nprint('Predicted labels:')\nprint(', '.join(str(map_predict_label(s)) for s in predictions.take(50)))")
predictions = trained_model.predict(test_data)
print('Ground Truth labels:')
print(', '.join(str(map_groundtruth_label(s.label.to_ndarray())) for s in test_data.take(50)))
print('Predicted labels:')
print(', '.join(str(map_predict_label(s)) for s in predictions.take(50)))


# In[71]:


labels = [map_groundtruth_label(s.label.to_ndarray()) for s in test_data.take(20000)]
df_prediction = pd.DataFrame({'Real Class' :np.array(labels)})
predicted_labels = [map_predict_label(s) for s in predictions.take(20000)]
df_prediction['Prediction'] = predicted_labels


# In[72]:


total_size = X_test.shape[0]
mismatch_size = df_prediction[ df_prediction['Real Class'] != df_prediction['Prediction'] ].size
accuracy = ((total_size - mismatch_size) / total_size) * 100
print(total_size)
print(mismatch_size)
print(accuracy)


# In[73]:


import datetime
now = datetime.datetime.now()

# with open("/tmp/model-attrib.properties", "w") as fp:
with open(model_attrib_file_path, "w") as fp:
    fp.write("width=" + str(lookback) + "\n")
    fp.write("mean=" + str(scaler_mean[0]) + "\n")
    fp.write("std=" + str(scaler_var[0]) + "\n")
    fp.write("input=input\n")
    fp.write("output=output\n")
    fp.write("generatedAt=" + str(datetime.datetime.now()) + "\n")


# In[74]:


with open(generation_complete_file_path, "w") as fp:
    fp.write(str(datetime.datetime.now()))

