param n_inner;
param n_cons; #Number of consumer nodes
param n_tgen; #number of convential generators
param n_rgen; #number of renewable generators
param n_storage; #number of storage nodes
param n_tot; # total number of nodes.
param m_factor; #multiplication factor per-unit MW
param pi; #pi constant
param totload; #total demand for consume rnodes
param cost_curt; #renewable cut costs
param cost_sl; # cost shedded load
param outname;

param current_hour;
param start_charge_time;
param end_charge_time;

#initialize variables 
set nodes := 0..n_tot;
set tgen := 0..n_tgen;
set consumers := n_tgen+1..(n_tgen+n_cons);
set inner := (n_tgen+n_cons+1)..(n_tgen+n_cons+n_inner);
set rgen :=  (n_tgen+n_cons+n_inner+1)..(n_tgen+n_cons+n_inner+n_rgen);
set storage := (n_tgen+n_cons+n_inner+n_rgen+1)..n_tot;

param weight {nodes,nodes} >=0;
param capacity {nodes,nodes} >=0;
param costs {tgen} >=0, default 0;
param rcost {rgen} >=0, default 0;
param rprodmax {rgen} >=0;  
param rprodmin {rgen} >=0;
param storagemin {storage};
param storagemax {storage};
param mintprod {tgen} >=0;
param maxtprod {tgen} >=0;
param loads {consumers} >=0;
param production {tgen} >= 0;
param rewproduction {rgen};
param flowfromstorage {storage};

#phase angle constraint 
var theta {nodes} >= -pi/2, <= pi/2;

#The function to minimize daily system cost.
minimize obj :
#	(sum{i in tgen} ((sum{j in nodes : capacity[i,j] <> 0} (theta[i]-theta[j])/ weight[i,j] )*costs[i])) +
#	(sum{i in rgen} ((sum{j in nodes : capacity[i,j] <> 0} (theta[i]-theta[j])/ weight[i,j] )*rcost[i])) + 
	(sum{i in rgen} cost_curt * (rprodmax[i] -(sum{j in nodes : capacity[i,j] <> 0} (theta[i]-theta[j])/weight[i,j])*m_factor)) +
	(sum{i in tgen} cost_sl	  *	(maxtprod[i] -(sum{j in nodes : capacity[i,j] <> 0} (theta[i]-theta[j])/weight[i,j])*m_factor));

#Subject to these constrains
subject to anglestability :
	theta[46], = 0;


# if later than  start_charge_time and earlier than end_charge_time, charge storage
for {{0}: current_hour >= start_charge_time || current_hour <= end_charge_time}{# IF condition THEN
	#charge storage
	#subject to setChargeStorage {i in storage}:
	 #
} 
for {{0}: current_hour <= start_charge_time || current_hour >= end_charge_time} {# ELSE
	# do not charge storage
}# ENDIF


#Maximum flow rate
subject to flowcapmax { i in nodes,j in nodes : capacity[i,j] <> 0} :
	((theta[i]-theta[j])/weight[i,j])*m_factor, <= capacity[i,j];

#Minimum flow rate
subject to flowcapmin { i in nodes,j in nodes : capacity[i,j] <> 0} :
	((theta[i]-theta[j])/weight[i,j])*m_factor, >= -capacity[i,j];

#Minimum generation of a renewable node
subject to flowcons { i in inner } :
	sum{ j in nodes : capacity[i,j] <> 0} ((theta[i]-theta[j])/weight[i,j])*m_factor, = sum{ j in nodes : capacity[j,i] <> 0} ((theta[j]-theta[i])/weight[j,i])*m_factor;

#Max generation of a renewable node
#subject to rgenmin { i in rgen } :
#	sum { j in nodes : capacity[i,j] <> 0} (theta[i]-theta[j])/weight[i,j]*m_factor, >= rprodmin[i];

#Minimum generation of a renewable node
#subject to rgenmax { i in rgen } :
#	sum { j in nodes : capacity[i,j] <> 0} ((theta[i]-theta[j])/weight[i,j])*m_factor, <= rprodmax[i];

# TODO vragen waarom deze uit staat? deze checkt of capaciteit van de lijn hoger is dan minimum van traditional production en maximum van traditional production, lijkt me dat deze uiteindelijk wel weer aan moet staan en dat de capaciteit van de lijnen aangepast moeten worden om aan deze constraint te voldoen.
#Minimum generation of a conventional generation node
#subject to genmin { i in tgen } :
#	sum { j in nodes : capacity[i,j] <> 0} (theta[i]-theta[j])/weight[i,j]*m_factor, >= mintprod[i];

#maximum dischage of conventional generation nodes
#subject to genmax { i in tgen } :
#	sum { j in nodes : capacity[i,j] <> 0} ((theta[i]-theta[j])/weight[i,j])*m_factor, <= maxtprod[i];

# todo julien vragen
subject to setRewProduction { i in rgen } :
	sum { j in nodes : capacity[i,j] <> 0} ((theta[i]-theta[j])/weight[i,j])*m_factor, = rewproduction[i];

# todo julien vragen	
subject to genproduction { i in tgen } :
	sum { j in nodes : capacity[i,j] <> 0} ((theta[i]-theta[j])/weight[i,j])*m_factor, = production[i];	

# TODO deze constriant wordt momenteel niet aan voldaan vragen waarom dat zo is aangezien dit niet zon gekke constraint is
# ../model.mod:114: storagemin[104.000000570012] out of domain
# ook lijkt storagemin momenteel negatief te kunnen zijn wat 0 of hoger zou moeten zijn lijkt me 
# constraint check op capiciteit van de lijn minimaal de storage min en max aan kan
#Minimum discharge of storage nodes
#subject to sgenmin { i in storage } :
#	sum { j in nodes : capacity[i,j] <> 0} (theta[i]-theta[j])/weight[i,j]*m_factor, >= storagemin[i];

#Demand of consumer nodes.
subject to sgenmax { i in storage } :
	sum { j in nodes : capacity[i,j] <> 0} ((theta[i]-theta[j])/weight[i,j])*m_factor, <= storagemax[i];

## evening
subject to storageFlow { i in storage } :
	sum { j in nodes : capacity[i,j] <> 0} ((theta[i]-theta[j])/weight[i,j])*m_factor, = flowfromstorage[i];		

#The amount of energy send to a consumer should be lower or equal to the load of the consumer
subject to loadfix {i in consumers} :
	sum { j in nodes : capacity[j,i] <> 0} ((theta[j]-theta[i])/weight[j,i])*m_factor, <= loads[i];
	
#Flow to a consumer can never be lower than zero
subject to loadMinValue {i in consumers} :	
	sum { j in nodes : capacity[j,i] <> 0} ((theta[j]-theta[i])/weight[j,i])*m_factor, >= 0;
	
#Balance supply and demand of energy.
subject to prodloadeq :
	sum { i in (rgen union tgen union storage), j in nodes : capacity[i,j] <> 0}
		((theta[i]-theta[j])/weight[i,j])*m_factor, =
		sum { i in consumers, j in nodes : capacity[j,i] <> 0}
		((theta[j]-theta[i])/weight[j,i])*m_factor;
	

#go ahead and solve the model
solve;
#display {i in nodes,j in nodes : capacity[i,j] <> 0}: i,j, ((theta[i] - theta[j])/ weight[i,j])*m_factor;
printf {i in storage,j in nodes : capacity [i,j] <> 0 } : "%d %d %.3f\n", i, j, ((theta[i] - theta[j])/ weight[i,j])*m_factor > "update.txt";

#For consumer nodes, generator nodes, renewable generator nodes
printf {i in nodes,j in nodes : capacity[i,j] <> 0} : "%d,%d,%.4f,%.4f,%.4f \n", i, j, ((theta[i] - theta[j])/ weight[i,j])*m_factor, (abs(((theta[i] - theta[j])/ weight[i,j])*m_factor)/capacity[i,j])*100, (abs(((theta[i] - theta[j])/ weight[i,j])*m_factor)/totload)*100 > "sol" & outname & ".txt";
printf : "\n" >> "sol" & outname & ".txt";

#Renewable energy curtailment
printf {i in rgen} : "R,%d,%.4f\n", i, (sum{j in nodes : capacity[i,j] <> 0} ((theta[i]-theta[j])/ weight[i,j])*m_factor) >> "sol" & outname & ".txt";
printf : "\n" >> "sol" & outname & ".txt";

#Traditional generators
printf {i in tgen} : "TG,%d, %.4f \n", i, (sum{j in nodes : capacity[i,j] <> 0} ((theta[i]-theta[j])/ weight[i,j])*m_factor) >> "sol" & outname & ".txt";
printf : "\n" >> "sol" & outname & ".txt";

#Consumers
printf {i in nodes, j in consumers : capacity[i,j] <> 0} : "C,%d, %.4f \n", j, ((theta[i] - theta[j])/ weight[i,j])*m_factor >> "sol" & outname & ".txt";
printf : "\n" >> "sol" & outname & ".txt";

#inner nodes
printf {i in inner} : "I,%d,%.4f \n", i, (sum{j in nodes : capacity[i,j] <> 0} ((theta[i]-theta[j])/ weight[i,j])*m_factor) >> "sol" & outname & ".txt";
printf : "\n" >> "sol" & outname & ".txt";

printf {i in storage} : "Stor,%d,%.4f \n", i, (sum{j in nodes : capacity[i,j] <> 0} ((theta[i]-theta[j])/ weight[i,j])*m_factor) >> "sol" & outname & ".txt";
printf : "\n" >> "sol" & outname & ".txt";
#printf {i in nodes,j in nodes : capacity[j,i] <> 0} : " flow in [ %d , %d ] = %.6f \n", j, i, ((theta[j] - theta[i])/ weight[j,i])*m_factor;
#printf {i in nodes} : "theta %d = %.6f\n", i, theta[i] >> "sol" & outname & ".txt"; 

#prints theta and the load for debugging purposes
#printf {i in consumers} : "Result theta: %d loads: %d \n", sum { j in nodes : capacity[j,i] <> 0} ((theta[j]-theta[i])/weight[j,i])*m_factor,  loads[i];

#printf {i in tgen, j in nodes : capacity[i,j] <> 0} : "Theta: %.4f, %.4f \n", theta[i], theta[j];
end;
