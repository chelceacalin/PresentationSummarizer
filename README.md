Simple project that helps users overcome the limitations of TOKEN_LIMIT for OpenAi Api while keeping high performance.

How it works:


<div style="width:100%">

* User defines the token limit for the model used, in my case 4000 
* We calculate the number of chunks we can split the text into which is CEIL(TEXT_LENGTH/TOKEN_SIZE)
* We iterate through the List<String> of texts and we create a virtual thread in which we call in paralel the api to summarize the text
* We wait for all the answers to come with a timelimit, and then we create a big chunk of text, making sure that the size of all the summaries is still < TOKEN_SIZE
* We create a prompt in which we tell the LLM to generate a json { title: 'identified title' , description: 'identified description' } based on the concatenation of the summaries

</div>

