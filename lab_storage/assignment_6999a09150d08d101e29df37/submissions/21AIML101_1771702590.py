import pandas as pd
from sklearn.model_selection import train_test_split
from sklearn.linear_model import LinearRegression
import joblib
from sklearn.impute import SimpleImputer

# Load the dataset
df = pd.read_csv('insurance.csv')
df.dropna()

# Preprocess the data
df['Gender'] = df['Gender'].map({'Male': 1, 'Female': 0})
df['Smoker'] = df['Smoker'].map({'Yes': 1, 'No': 0})
df['Region'] = df['Region'].astype('category').cat.codes  # Convert categorical to numerical

# Features and target variable
X = df[['Age', 'Gender', 'BMI', 'Children', 'Smoker', 'Region']]
y = df['Charges']

imputer = SimpleImputer(strategy='constant')  # Replace 'mean' with desired strategy
X_imputed = imputer.fit_transform(X) 

# Split the data
X_train, X_test, y_train, y_test = train_test_split(X, y, test_size=0.2, random_state=42)

# Train the model
model = LinearRegression()
model.fit(X_imputed, y)

# Save the model
joblib.dump(model, 'insurance_model.pkl')