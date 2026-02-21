import streamlit as st
import pickle
import pandas as pd
import numpy as np
import joblib

# Load the model using joblib
model = joblib.load('insurance_model.pkl') 


# Define the app
def main():
    st.title("Medical Insurance Premium Prediction")
    st.write("Enter the details below to predict the insurance premium.")

    # Input fields
    age = st.slider("Age", min_value=18, max_value=100, value=30)
    gender = st.selectbox("Gender", ["Male", "Female"])
    bmi = st.number_input("BMI (Body Mass Index)", min_value=10.0, max_value=50.0, value=25.0, step=0.1)
    children = st.slider("Number of Dependents", min_value=0, max_value=5, value=0)
    smoker = st.selectbox("Smoker", ["Yes", "No"])
    region = st.selectbox("Region", ["Northwest", "Northeast", "Southeast", "Southwest"])

    # Process inputs
    gender_encoded = 1 if gender == "Male" else 0
    smoker_encoded = 1 if smoker == "Yes" else 0
    region_encoded = ["Northwest", "Northeast", "Southeast", "Southwest"].index(region)

    # Create input array for the model
    input_data = pd.DataFrame([[age, gender_encoded, bmi, children, smoker_encoded, region_encoded]])
    #input_data1 = pd.DataFrame(input_data)

    # Prediction
    if st.button("Predict"):
        prediction = model.predict(input_data)[0]
        st.success(f"The predicted insurance premium is: ₹{prediction:.2f}")

    # Additional visualizations (optional)
    st.write("Optional: Add visualizations or additional insights.")


if __name__ == "__main__":
    main()


