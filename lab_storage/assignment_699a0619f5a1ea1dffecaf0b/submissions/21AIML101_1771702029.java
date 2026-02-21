class CarAssembly implements Runnable
{
  private String componentName;
  private int timeToPrepare;
  
  
  public CarAssembly(String componentName , int timeToPrepare){
    this.componentName = componentName;
    this.timeToPrepare = timeToPrepare;
  }
  
  
  public void run(){
    try{
      
        System.out.println(componentName + " is preparing.");
        Thread.sleep(timeToPrepare);
        System.out.println(componentName + " is ready.");
    }catch(InterruptedException e){
      System.out.println(e.getMessage());
    }
  }
  
  public static void main(String args[]){
    CarAssembly engine = new CarAssembly("Engine",154);
    CarAssembly body = new CarAssembly("Body",160);
    CarAssembly wheels = new CarAssembly("Wheels",170);
    
    Thread engineThread = new Thread(engine);
    Thread bodyThread = new Thread(body);
    Thread wheelThread = new Thread(wheels);
    
    try{
      
      engineThread.start();
      engineThread.join();
      
      bodyThread.start();
      bodyThread.join();
      
      wheelThread.start();
      wheelThread.join();
    }catch(InterruptedException e){
      System.out.println(e.getMessage());
    }
  }
}
  





















































































            
