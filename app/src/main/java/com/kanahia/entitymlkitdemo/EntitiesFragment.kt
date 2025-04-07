package com.kanahia.entitymlkitdemo

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.navArgs
import androidx.recyclerview.widget.RecyclerView
import com.kanahia.entitymlkitdemo.databinding.FragmentEntitiesBinding
import com.kanahia.entitymlkitdemo.databinding.ItemEntityBinding

class EntitiesFragment : Fragment() {
    private var _binding: FragmentEntitiesBinding? = null
    private val binding get() = _binding!!

    private val args: EntitiesFragmentArgs by navArgs()

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentEntitiesBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val entities = args.entities.toList()

        if (entities.isEmpty()) {
            binding.entitiesRecyclerView.visibility = View.GONE
            binding.emptyView.visibility = View.VISIBLE
        } else {
            binding.entitiesRecyclerView.visibility = View.VISIBLE
            binding.emptyView.visibility = View.GONE
            binding.entitiesRecyclerView.adapter = EntitiesAdapter(entities)
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    private inner class EntitiesAdapter(
        private val entities: List<EntityItem>
    ) : RecyclerView.Adapter<EntitiesAdapter.EntityViewHolder>() {

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): EntityViewHolder {
            val binding = ItemEntityBinding.inflate(
                LayoutInflater.from(parent.context),
                parent,
                false
            )
            return EntityViewHolder(binding)
        }

        override fun onBindViewHolder(holder: EntityViewHolder, position: Int) {
            holder.bind(entities[position])
        }

        override fun getItemCount(): Int = entities.size

        inner class EntityViewHolder(
            private val binding: ItemEntityBinding
        ) : RecyclerView.ViewHolder(binding.root) {

            fun bind(entity: EntityItem) {
                binding.entityNameTextView.text = entity.name
                binding.entityTypeTextView.text = entity.type
                binding.entityDescriptionTextView.text = entity.description
            }
        }
    }
}